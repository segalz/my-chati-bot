package app.mychatibot.eyes

/**
 * Owner-editable phrasebook (assets/phrasebook.txt): exact matches of short, common phrases get an
 * instant reaction without the model. Matching is deliberately exact after light normalization,
 * so "not happy" can never hit "happy".
 */
class Phrasebook private constructor(private val entries: Map<String, Pair<Expression, Look>>) {

    val size: Int get() = entries.size

    fun lookup(sentence: String): Pair<Expression, Look>? = entries[normalize(sentence)]

    companion object {
        private val EDGE_PUNCTUATION = Regex("""^[\s.,!?;:…"'()-]+|[\s.,!?;:…"'()-]+$""")
        private val SPACES = Regex("""\s+""")

        fun normalize(text: String): String =
            text.lowercase().replace('’', '\'').replace(EDGE_PUNCTUATION, "").replace(SPACES, " ")

        /**
         * Parses `EXPRESSION [look]: phrase | phrase` lines; `#` starts a comment. Bad lines are
         * reported in [errors] and skipped; a phrase listed twice keeps its first entry.
         */
        fun parse(text: String, errors: MutableList<String> = mutableListOf()): Phrasebook {
            val entries = LinkedHashMap<String, Pair<Expression, Look>>()
            text.lineSequence().forEachIndexed { index, raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachIndexed
                val head = line.substringBefore(':', missingDelimiterValue = "").trim().split(SPACES)
                val phrases = line.substringAfter(':', missingDelimiterValue = "")
                val expression = Expression.entries.firstOrNull { it.name == head.getOrNull(0) }
                val look = if (head.size > 1) Look.entries.firstOrNull { it.name.lowercase() == head[1] } else Look.CENTER
                if (expression == null || look == null || head.size > 2 || phrases.isBlank()) {
                    errors += "line ${index + 1}: cannot parse '$raw'"
                    return@forEachIndexed
                }
                phrases.split('|').map(::normalize).filter { it.isNotEmpty() }.forEach { phrase ->
                    if (phrase in entries) errors += "line ${index + 1}: duplicate phrase '$phrase' ignored"
                    else entries[phrase] = expression to look
                }
            }
            return Phrasebook(entries)
        }

        val EMPTY = Phrasebook(emptyMap())
    }
}
