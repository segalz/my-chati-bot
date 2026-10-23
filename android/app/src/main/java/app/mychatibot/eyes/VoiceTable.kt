package app.mychatibot.eyes

/** How one sentence is spoken: which speaker of the voice model, and how fast (1.0 = normal). */
data class VoiceStyle(val speaker: Int, val speed: Float)

/**
 * Expression → voice style. Deterministic and owner-editable (`assets/voice.txt`): the eye model
 * only picks the expression (R2); how that sounds is decided here. Expressions not listed, NONE,
 * and failed decisions use the NEUTRAL style.
 */
class VoiceTable private constructor(private val styles: Map<Expression, VoiceStyle>) {

    fun styleFor(expression: Expression?): VoiceStyle =
        expression?.let { styles[it] } ?: styles[Expression.NEUTRAL] ?: NEUTRAL_STYLE

    val size: Int get() = styles.size

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
        const val MAX_SPEAKER = 9_999
        private val NEUTRAL_STYLE = VoiceStyle(0, 1.0f)

        /** Used when the asset is missing; matches the Stage 8.2 samples. */
        val DEFAULT = VoiceTable(
            mapOf(
                Expression.NEUTRAL to NEUTRAL_STYLE,
                Expression.HAPPY to VoiceStyle(0, 1.10f),
                Expression.SAD to VoiceStyle(0, 0.85f),
                Expression.ANGRY to VoiceStyle(0, 1.05f),
                Expression.SURPRISED to VoiceStyle(0, 1.15f),
                Expression.SLEEPY to VoiceStyle(0, 0.85f),
                Expression.BORED to VoiceStyle(0, 0.90f),
            ),
        )

        private val LINE = Regex("""([A-Z_]+)\s+speaker=(\d+)\s+speed=(\d+(?:\.\d+)?)""")

        /**
         * One line per expression: `HAPPY speaker=0 speed=1.10`. Blank lines and `#` comments are
         * ignored; a malformed, unknown, out-of-range or repeated line is reported in [errors] and
         * skipped. Expressions not listed keep their [DEFAULT] style.
         */
        fun parse(text: String, errors: MutableList<String>): VoiceTable {
            val styles = DEFAULT.styles.toMutableMap()
            val seen = mutableSetOf<Expression>()
            for ((n, raw) in text.lines().withIndex()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val m = LINE.matchEntire(line)
                if (m == null) {
                    errors += "line ${n + 1}: expected 'EXPRESSION speaker=N speed=F'"
                    continue
                }
                val expression = Expression.entries.firstOrNull { it.name == m.groupValues[1] }
                val speaker = m.groupValues[2].toIntOrNull()
                val speed = m.groupValues[3].toFloatOrNull()
                when {
                    expression == null || expression == Expression.NONE ->
                        errors += "line ${n + 1}: unknown expression ${m.groupValues[1]}"
                    speaker == null || speaker > MAX_SPEAKER -> errors += "line ${n + 1}: speaker out of range"
                    speed == null || speed !in MIN_SPEED..MAX_SPEED ->
                        errors += "line ${n + 1}: speed must be $MIN_SPEED..$MAX_SPEED"
                    !seen.add(expression) -> errors += "line ${n + 1}: ${expression.name} repeated"
                    else -> styles[expression] = VoiceStyle(speaker, speed)
                }
            }
            return VoiceTable(styles)
        }
    }
}
