package app.mychatibot.eyes

/**
 * Eye color by state and emotion (owner, 2026-10-24): the original electric blue while waiting,
 * green while the phone speaks, a light tint toward each emotion's color, and strong emotions in
 * their own color (anger red). Deterministic and app-owned: the model only picks the expression
 * (R2). Colors are 0xRRGGBB.
 */
object EyeColors {
    /** The original Monochrome Blue drawer's own color. */
    const val WAITING = 0x00BFFF
    const val SPEAKING = 0x00E676
    /** How far a mild emotion pulls the base color toward its own (0..1). */
    const val TINT_AMOUNT = 0.35f
    const val FADE_MS = 300

    private val TINT = mapOf(
        Expression.HAPPY to 0xFFD54F,      // warm yellow
        Expression.SAD to 0x3D5AFE,        // deep blue
        Expression.SURPRISED to 0xFFFFFF,  // white
        Expression.SLEEPY to 0x9575CD,     // lavender
        Expression.BORED to 0x9E9E9E,      // grey
        Expression.WINK_LEFT to 0xFFD54F,
        Expression.WINK_RIGHT to 0xFFD54F,
    )
    private val STRONG = mapOf(Expression.ANGRY to 0xFF2A2A)

    /** The color for the eyes now: [speaking] picks the base, [expression] (null = idle) tints it. */
    fun colorFor(speaking: Boolean, expression: Expression?): Int {
        STRONG[expression]?.let { return it }
        val base = if (speaking) SPEAKING else WAITING
        val tint = TINT[expression] ?: return base
        return blend(base, tint, TINT_AMOUNT)
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xff
            val y = (b shr shift) and 0xff
            return Math.round(x + (y - x) * t).coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
