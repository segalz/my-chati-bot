package app.mychatibot.eyes

/**
 * Turns a longer English text (a story, a reply in a conversation) into sentences and gives each
 * one a speaking time, so the eyes can follow the text at the pace it would be spoken. Pure and
 * deterministic: no model involved.
 */
object StoryText {
    const val MAX_TEXT_CHARS = 4_000
    const val MAX_SENTENCES = 40
    const val MAX_SENTENCE_CHARS = 300
    const val MIN_SPOKEN_MS = 1_200L
    const val PAUSE_MS = 400L
    const val DEFAULT_WORDS_PER_SECOND = 2.5f

    /** Words ending in a period that do not end a sentence. */
    private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "vs", "etc", "e.g", "i.e", "prof", "sr", "jr")
    private val END = Regex("""[.!?…]+["'”’»)\]]*""")

    /**
     * Splits [text] at sentence ends (. ! ? … optionally followed by closing quotes or brackets,
     * then whitespace) and at line breaks. Text beyond [MAX_TEXT_CHARS] and sentences beyond
     * [MAX_SENTENCES] are dropped; an over-long sentence is cut at whitespace into pieces of at most
     * [MAX_SENTENCE_CHARS].
     */
    fun split(text: String): List<String> {
        val sentences = mutableListOf<String>()
        for (line in text.take(MAX_TEXT_CHARS).split('\n', '\r')) {
            var start = 0
            for (m in END.findAll(line)) {
                val end = m.range.last + 1
                if (end < line.length && !line[end].isWhitespace()) continue  // "3.5", "a.m.", "e.g."
                if (m.value.startsWith('.') && m.value.length == 1 && isAbbreviation(line, m.range.first)) continue
                addPieces(line.substring(start, end), sentences)
                start = end
            }
            addPieces(line.substring(start), sentences)
        }
        return sentences.take(MAX_SENTENCES)
    }

    /** How long [sentence] takes to say: words at [wordsPerSecond] plus a pause, at least [MIN_SPOKEN_MS]. */
    fun spokenMs(sentence: String, wordsPerSecond: Float = DEFAULT_WORDS_PER_SECOND): Long {
        val words = sentence.split(' ').count { it.any(Char::isLetterOrDigit) }
        return maxOf(MIN_SPOKEN_MS, (words * 1_000f / wordsPerSecond).toLong() + PAUSE_MS)
    }

    private fun isAbbreviation(line: String, dot: Int): Boolean {
        var i = dot
        while (i > 0 && (line[i - 1].isLetter() || line[i - 1] == '.')) i--
        return line.substring(i, dot).lowercase() in ABBREVIATIONS
    }

    private fun addPieces(raw: String, out: MutableList<String>) {
        var rest = raw.trim().replace(Regex("""\s+"""), " ")
        while (rest.length > MAX_SENTENCE_CHARS) {
            val cut = rest.lastIndexOf(' ', MAX_SENTENCE_CHARS).takeIf { it > 0 } ?: MAX_SENTENCE_CHARS
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.any(Char::isLetterOrDigit)) out += rest
    }
}
