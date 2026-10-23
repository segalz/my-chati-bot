package app.mychatibot.eyes

/**
 * Decides the gaze from the sentence with conservative keyword rules (the eye model only picks
 * the expression). The eyes look somewhere only when the sentence clearly asks for it:
 * - a look verb followed directly (allowing a few linking words) by a direction or a place:
 *   "look left", "glance over to the right", "look up at the ceiling", "check the floor";
 * - "up"/"down" right after the verb only when they are not a phrasal verb ("look up the
 *   invoice" stays centered);
 * - a side relative to the listener without a verb: "a cat on your right".
 * Any negated look verb ("don't look", "do not ever look") keeps the eyes centered.
 */
object LookRules {
    private const val MAX_LINKING = 4
    private val VERBS = setOf("look", "looking", "glance", "peek", "stare", "gaze", "check")
    private val TURN_OBJECTS = setOf("your", "my", "eyes", "head", "to", "toward", "towards")
    private val NEGATIONS = setOf("dont", "don't", "not", "never", "stop")
    private val LINKING = setOf(
        "to", "at", "over", "toward", "towards", "the", "your", "my", "a", "bit", "little",
        "slightly", "there", "here", "quickly", "please", "now",
    )
    private val SIDES = mapOf("left" to Look.LEFT, "right" to Look.RIGHT)
    private val UP_DOWN = mapOf(
        "up" to Look.UP, "upward" to Look.UP, "upwards" to Look.UP,
        "down" to Look.DOWN, "downward" to Look.DOWN, "downwards" to Look.DOWN,
    )
    /** Words that may follow "up"/"down" when they mean a direction (end of sentence also counts). */
    private val AFTER_UP_DOWN = setOf("at", "to", "toward", "towards", "for", "please", "here", "there", "now",
        "again", "slightly", "and", "then", "too")
    private val PLACES = mapOf(
        "ceiling" to Look.UP, "sky" to Look.UP, "roof" to Look.UP, "above" to Look.UP,
        "floor" to Look.DOWN, "ground" to Look.DOWN, "below" to Look.DOWN, "feet" to Look.DOWN,
    )

    fun detect(sentence: String): Look {
        val words = Regex("""[a-z']+""").findAll(sentence.lowercase().replace('’', '\'')).map { it.value }.toList()
        val verbAt = words.indices.filter { isLookVerb(words, it) }
        if (verbAt.any { isNegated(words, it) }) return Look.CENTER

        for (v in verbAt) {
            var j = v + 1
            if (words[v] == "turn") while (j < words.size && words[j] in setOf("your", "my", "eyes", "head")) j++
            var linking = 0
            while (j < words.size) {
                val w = words[j]
                SIDES[w]?.let { return it }
                PLACES[w]?.let { return it }
                UP_DOWN[w]?.let { dir ->
                    val next = words.getOrNull(j + 1)
                    if (next == null || next in AFTER_UP_DOWN) return dir
                }
                if (w !in LINKING || ++linking > MAX_LINKING) break
                j++
            }
        }
        // A side relative to the listener, no verb needed: "on your right", "to your left".
        for (i in 0 until words.size - 2) {
            if (words[i] in setOf("to", "on", "at") && words[i + 1] in setOf("the", "your", "my")) {
                SIDES[words[i + 2]]?.let { return it }
            }
        }
        return Look.CENTER
    }

    private fun isLookVerb(words: List<String>, i: Int): Boolean =
        words[i] in VERBS || (words[i] == "turn" && words.getOrNull(i + 1) in TURN_OBJECTS)

    /** "don't look", "do not look", "never look", "stop looking", "do not ever look", "no need to look". */
    private fun isNegated(words: List<String>, verb: Int): Boolean {
        val before = words.subList(maxOf(0, verb - 3), verb)
        if (before.any { it in NEGATIONS }) return true
        return before.windowed(2).any { it == listOf("no", "need") }
    }
}
