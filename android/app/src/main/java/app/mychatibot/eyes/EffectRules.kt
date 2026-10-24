package app.mychatibot.eyes

/** Bit layout shared with EffectsOverlay. No Android dependencies. */
object EffectRules {
    const val SWEAT = 1 shl 0
    const val ANGER_MARK = 1 shl 1
    const val SPARKLES = 1 shl 2
    const val QUESTION = 1 shl 3
    const val EXCLAIM = 1 shl 4
    const val NOTES = 1 shl 5
    const val RAIN = 1 shl 6

    /** [sincePreviousMs] is elapsed time since the previous shown expression was replaced. */
    fun effectsFor(
        expression: Expression?, strength: Float, speaking: Boolean,
        previous: Expression?, sincePreviousMs: Long, on: (String) -> Boolean,
    ): Int {
        var mask = 0
        when (expression) {
            Expression.ANGRY -> if (on("fx_anger")) mask = mask or ANGER_MARK
            Expression.SAD -> if (strength >= 0.6f && on("fx_rain")) mask = mask or RAIN
            Expression.HAPPY -> {
                if (strength >= 0.7f && on("fx_sparkles")) mask = mask or SPARKLES
                if (speaking && on("fx_notes")) mask = mask or NOTES
            }
            Expression.SURPRISED -> {
                if (on("fx_qmark")) {
                    if (strength >= 0.5f) mask = mask or EXCLAIM
                    else if (strength < 0.5f) mask = mask or QUESTION
                }
                if (previous == Expression.SAD && sincePreviousMs in 0L until 4_000L && on("fx_sweat")) {
                    mask = mask or SWEAT
                }
            }
            else -> Unit
        }
        return mask
    }
}
