package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectRulesTest {
    private fun mask(
        expression: Expression?, strength: Float = 0.5f, speaking: Boolean = false,
        previous: Expression? = null, elapsed: Long = 0, enabled: Set<String>? = null,
    ) = EffectRules.effectsFor(expression, strength, speaking, previous, elapsed) {
        enabled == null || it in enabled
    }

    @Test fun angerIsIndependentOfStrength() {
        for (strength in listOf(0f, 0.5f, 1f))
            assertEquals(EffectRules.ANGER_MARK, mask(Expression.ANGRY, strength))
    }

    @Test fun strengthThresholds() {
        assertEquals(0, mask(Expression.SAD, 0.5999f))
        assertEquals(EffectRules.RAIN, mask(Expression.SAD, 0.6f))
        assertEquals(EffectRules.RAIN, mask(Expression.SAD, 1f))
        assertEquals(0, mask(Expression.HAPPY, 0.6999f))
        assertEquals(EffectRules.SPARKLES, mask(Expression.HAPPY, 0.7f))
        assertEquals(EffectRules.SPARKLES, mask(Expression.HAPPY, 1f))
        assertEquals(EffectRules.QUESTION, mask(Expression.SURPRISED, 0f))
        assertEquals(EffectRules.QUESTION, mask(Expression.SURPRISED, 0.4999f))
        assertEquals(EffectRules.EXCLAIM, mask(Expression.SURPRISED, 0.5f))
        assertEquals(EffectRules.EXCLAIM, mask(Expression.SURPRISED, 1f))
    }

    @Test fun notesRequireHappyAndSpeaking() {
        assertEquals(EffectRules.NOTES, mask(Expression.HAPPY, 0f, true))
        assertEquals(0, mask(Expression.HAPPY, 0f, false))
        assertEquals(EffectRules.NOTES or EffectRules.SPARKLES, mask(Expression.HAPPY, 1f, true))
        assertEquals(EffectRules.RAIN, mask(Expression.SAD, 1f, true))
        assertEquals(0, mask(Expression.NEUTRAL, 1f, true))
    }

    @Test fun sweatWindowAndPreviousExpression() {
        for (elapsed in listOf(0L, 1L, 3_999L))
            assertEquals(EffectRules.SWEAT, mask(Expression.SURPRISED, previous = Expression.SAD,
                elapsed = elapsed, enabled = setOf("fx_sweat")))
        for (elapsed in listOf(-1L, 4_000L, 4_001L, Long.MAX_VALUE))
            assertEquals(0, mask(Expression.SURPRISED, previous = Expression.SAD,
                elapsed = elapsed, enabled = setOf("fx_sweat")))
        for (previous in listOf(null, Expression.HAPPY, Expression.SURPRISED))
            assertEquals(0, mask(Expression.SURPRISED, previous = previous, enabled = setOf("fx_sweat")))
        assertEquals(0, mask(Expression.HAPPY, previous = Expression.SAD, enabled = setOf("fx_sweat")))
        assertEquals(EffectRules.SWEAT or EffectRules.EXCLAIM,
            mask(Expression.SURPRISED, previous = Expression.SAD))
    }

    @Test fun eachSwitchControlsOnlyItsOwnEffect() {
        val cases = listOf(
            Triple("fx_anger", Expression.ANGRY, EffectRules.ANGER_MARK),
            Triple("fx_rain", Expression.SAD, EffectRules.RAIN),
            Triple("fx_sparkles", Expression.HAPPY, EffectRules.SPARKLES),
            Triple("fx_notes", Expression.HAPPY, EffectRules.NOTES),
            Triple("fx_qmark", Expression.SURPRISED, EffectRules.EXCLAIM),
            Triple("fx_sweat", Expression.SURPRISED, EffectRules.SWEAT),
        )
        for ((name, expression, expected) in cases) {
            assertEquals(name, expected, mask(expression, 1f, true, Expression.SAD, enabled = setOf(name)))
            assertEquals(name, 0, mask(expression, 1f, true, Expression.SAD,
                enabled = cases.map { it.first }.toSet() - name) and expected)
        }
        assertEquals(EffectRules.QUESTION, mask(Expression.SURPRISED, 0f, enabled = setOf("fx_qmark")))
        assertEquals(0, mask(Expression.SURPRISED, 0f, enabled = emptySet()))
    }

    @Test fun switchesOffDisableEveryRule() {
        for (expression in Expression.entries) for (strength in listOf(0f, 0.5f, 1f))
            assertEquals(0, mask(expression, strength, true, Expression.SAD, enabled = emptySet()))
    }

    @Test fun nullAndOtherExpressionsHaveNoEffects() {
        for (expression in listOf(null, Expression.NEUTRAL, Expression.NONE, Expression.SLEEPY,
            Expression.BORED, Expression.WINK_LEFT, Expression.WINK_RIGHT))
            assertEquals(0, mask(expression, 1f, true, Expression.SAD))
    }
}
