package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeControllerTest {

    private class FakeSink : EyeSink {
        val calls = mutableListOf<String>()
        override fun setExpression(engineId: Int) { calls += "expr:${Expression.entries[engineId]}" }
        override fun lookAt(x: Float, y: Float) { calls += "look:$x,$y" }
    }

    private val sink = FakeSink()
    // Story tests below assume these rules (the app default carry is tuned separately).
    private val controller = EyeController(sink, minDwellMs = 800, defaultHoldMs = 3_000)
        .apply { rules = MoodRules(lingerMs = 1_500, carryMs = 4_000, dwellMs = 1_500) }

    private fun intent(e: Expression, l: Look = Look.CENTER, hold: Long? = null, expiresAt: Long? = null) =
        EyeIntent(e, l, IntentSource.DEBUG, holdMs = hold, expiresAtMs = expiresAt)

    @Test fun appliesExpressionAndGaze() {
        assertEquals(Outcome.APPLIED, controller.submit(intent(Expression.HAPPY, Look.LEFT), 0))
        assertEquals(listOf("expr:HAPPY", "look:-1.0,0.0"), sink.calls)
        assertEquals(Expression.HAPPY, controller.state.active?.expression)
    }

    @Test fun noneDoesNotTouchTheEyes() {
        assertEquals(Outcome.IGNORED_NO_REACTION, controller.submit(intent(Expression.NONE), 0))
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun neutralCenterIsNoChange() {
        assertEquals(Outcome.IGNORED_NO_CHANGE, controller.submit(intent(Expression.NEUTRAL), 0))
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun neutralWithGazeIsApplied() {
        assertEquals(Outcome.APPLIED, controller.submit(intent(Expression.NEUTRAL, Look.UP), 0))
        assertEquals(listOf("expr:NEUTRAL", "look:0.0,-1.0"), sink.calls)
    }

    @Test fun expiredIntentRejected() {
        assertEquals(Outcome.REJECTED_EXPIRED, controller.submit(intent(Expression.SAD, expiresAt = 500), 500))
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun holdExpiryReturnsToIdle() {
        controller.submit(intent(Expression.ANGRY), 0)
        assertFalse(controller.tick(2_999))
        assertTrue(controller.tick(3_000))
        assertEquals(listOf("expr:ANGRY", "look:0.0,0.0", "expr:NEUTRAL", "look:0.0,0.0"), sink.calls)
        assertNull(controller.state.active)
    }

    @Test fun customHoldIsUsed() {
        controller.submit(intent(Expression.SLEEPY, hold = 5_000), 0)
        assertFalse(controller.tick(4_999))
        assertTrue(controller.tick(5_000))
    }

    @Test fun holdNeverShorterThanDwell() {
        controller.submit(intent(Expression.HAPPY, hold = 300), 0)
        assertFalse(controller.tick(500))
        assertTrue(controller.tick(800))
    }

    @Test fun newestWinsAfterMinimumDwell() {
        controller.submit(intent(Expression.HAPPY), 0)
        assertEquals(Outcome.QUEUED, controller.submit(intent(Expression.SAD), 100))
        assertEquals(Outcome.QUEUED, controller.submit(intent(Expression.SURPRISED), 200)) // replaces SAD
        assertFalse(controller.tick(799))
        assertTrue(controller.tick(800))
        assertEquals(Expression.SURPRISED, controller.state.active?.expression)
        assertFalse(sink.calls.contains("expr:SAD"))
    }

    @Test fun afterDwellNewIntentAppliesImmediately() {
        controller.submit(intent(Expression.HAPPY), 0)
        assertEquals(Outcome.APPLIED, controller.submit(intent(Expression.BORED), 800))
        assertEquals(Expression.BORED, controller.state.active?.expression)
    }

    @Test fun immediateApplyDiscardsOlderPending() {
        // Codex review: SAD queued at 100, BORED applied at 800 before a tick → SAD must not replay.
        controller.submit(intent(Expression.HAPPY), 0)
        assertEquals(Outcome.QUEUED, controller.submit(intent(Expression.SAD), 100))
        assertEquals(Outcome.APPLIED, controller.submit(intent(Expression.BORED), 800))
        assertNull(controller.state.pending)
        controller.tick(1_600)
        assertEquals(Expression.BORED, controller.state.active?.expression)
        assertFalse(sink.calls.contains("expr:SAD"))
    }

    @Test fun noReactionWhilePendingKeepsPending() {
        controller.submit(intent(Expression.HAPPY), 0)
        controller.submit(intent(Expression.SAD), 100)
        assertEquals(Outcome.IGNORED_NO_REACTION, controller.submit(intent(Expression.NONE), 200))
        assertEquals(Expression.SAD, controller.state.pending?.expression)
    }

    @Test fun holdArithmeticSaturates() {
        controller.submit(intent(Expression.HAPPY), Long.MAX_VALUE - 10)
        assertEquals(Long.MAX_VALUE, controller.state.holdUntilMs)
    }

    @Test fun queuedIntentThatExpiresIsDropped() {
        controller.submit(intent(Expression.HAPPY), 0)
        controller.submit(intent(Expression.SAD, expiresAt = 600), 100)
        controller.tick(800)
        assertEquals(Expression.HAPPY, controller.state.active?.expression)
        assertNull(controller.state.pending)
    }

    // --- Story mode (sentence spoken for a given time; rules: linger 1500, carry 4000, dwell 1500) ---

    @Test fun storyReactionHoldsForSentencePlusLinger() {
        assertEquals(Outcome.APPLIED, controller.submit(intent(Expression.SAD), 0, spokenMs = 2_000))
        assertFalse(controller.tick(3_499))
        assertTrue(controller.tick(3_500))
        assertNull(controller.state.active)
    }

    @Test fun storyNeutralSentenceCarriesMoodWithinLimit() {
        controller.submit(intent(Expression.SAD), 0, spokenMs = 2_000)        // carry until 6000
        assertEquals(Outcome.CARRIED, controller.submit(intent(Expression.NONE), 2_000, spokenMs = 3_000))
        assertFalse(controller.tick(4_999))                                   // held to the end of the sentence
        assertEquals(Outcome.CARRIED, controller.submit(intent(Expression.NEUTRAL), 5_000, spokenMs = 3_000))
        assertFalse(controller.tick(5_999))
        assertTrue(controller.tick(6_000))                                    // carry limit reached
        assertEquals(Outcome.IGNORED_NO_REACTION, controller.submit(intent(Expression.NONE), 6_100, spokenMs = 2_000))
        assertEquals(listOf("expr:SAD", "look:0.0,0.0", "expr:NEUTRAL", "look:0.0,0.0"), sink.calls)
    }

    @Test fun storySameReactionExtendsWithoutRestart() {
        controller.submit(intent(Expression.HAPPY), 0, spokenMs = 2_000)
        assertEquals(Outcome.EXTENDED, controller.submit(intent(Expression.HAPPY), 2_000, spokenMs = 2_000))
        assertFalse(controller.tick(5_499))
        assertTrue(controller.tick(5_500))
        assertEquals(1, sink.calls.count { it == "expr:HAPPY" })
    }

    @Test fun storyLookRequestKeepsMood() {
        controller.submit(intent(Expression.HAPPY), 0, spokenMs = 2_000)
        assertEquals(Outcome.APPLIED, controller.submit(intent(Expression.NEUTRAL, Look.LEFT), 2_000, spokenMs = 2_000))
        assertEquals(Expression.HAPPY, controller.state.active?.expression)
        assertEquals(Look.LEFT, controller.state.active?.look)
    }

    @Test fun storyChangeWaitsForStoryDwell() {
        controller.submit(intent(Expression.SAD), 0, spokenMs = 1_200)
        assertEquals(Outcome.QUEUED, controller.submit(intent(Expression.HAPPY), 1_200, spokenMs = 2_000))
        assertFalse(controller.tick(1_499))
        assertTrue(controller.tick(1_500))
        assertEquals(Expression.HAPPY, controller.state.active?.expression)
        // held to the queued sentence's end (3200) + linger, not from when it was finally shown
        assertFalse(controller.tick(4_699))
        assertTrue(controller.tick(4_700))
    }

    @Test fun storyRulesAreTunable() {
        controller.rules = MoodRules(lingerMs = 0, carryMs = 0, dwellMs = 0)
        controller.submit(intent(Expression.SAD), 0, spokenMs = 2_000)
        assertEquals(Outcome.IGNORED_NO_REACTION, controller.submit(intent(Expression.NONE), 2_000, spokenMs = 2_000))
        assertTrue(controller.tick(2_000))
    }

    @Test fun plainIntentAfterStoryHasNoCarry() {
        controller.submit(intent(Expression.SAD), 0, spokenMs = 2_000)
        controller.submit(intent(Expression.HAPPY), 2_000)                    // single sentence, 3 s hold
        assertEquals(Outcome.IGNORED_NO_REACTION, controller.submit(intent(Expression.NONE), 3_000, spokenMs = 2_000))
        assertTrue(controller.tick(5_000))
    }

    // --- Idle glances (owner: after 15 s of quiet the eyes glance aside) ---

    private fun glancer(vararg sides: Look): EyeController {
        val queue = ArrayDeque(sides.toList())
        return EyeController(sink, idleGlanceMs = 15_000, glanceMs = 1_000, chooseSide = { queue.removeFirst() })
    }

    @Test fun idleGlanceAfterQuietThenBackToCenter() {
        val c = glancer(Look.RIGHT, Look.LEFT)
        assertFalse(c.tick(0))                      // starts the quiet clock
        assertFalse(c.tick(14_999))
        assertTrue(c.tick(15_000))                  // glance right
        assertEquals(listOf("look:1.0,0.0"), sink.calls)
        assertTrue(c.tick(16_000))                  // back to center
        assertFalse(c.tick(30_999))
        assertTrue(c.tick(31_000))                  // next glance 15 s after the last one ended
        assertEquals(listOf("look:1.0,0.0", "look:0.0,0.0", "look:-1.0,0.0"), sink.calls)
    }

    @Test fun noGlanceWhileShowingAndReactionReplacesGlance() {
        val c = glancer(Look.LEFT, Look.RIGHT)
        c.tick(0)
        c.submit(intent(Expression.HAPPY), 1_000)   // held until 4000, then idle
        assertFalse(c.tick(3_999))
        assertTrue(c.tick(4_000))                   // back to idle: quiet clock restarts at the next tick
        c.tick(4_100)
        assertFalse(c.tick(19_099))
        assertTrue(c.tick(19_100))                  // glance left
        assertEquals(Outcome.APPLIED, c.submit(intent(Expression.SAD), 19_200))  // no dwell from a glance
        assertFalse(c.tick(20_100))                 // the glance's end does not recenter over SAD
        assertEquals(Expression.SAD, c.state.active?.expression)
    }

    @Test fun glanceOnlyWhenIdle() {
        val c = glancer()
        c.submit(intent(Expression.HAPPY), 0)
        assertFalse(c.glance(Look.RIGHT, 100))
        assertTrue(EyeController(sink).glance(Look.LEFT, 0))  // idle glances off by default, explicit ones work
    }

    // --- Codex review fixes ---

    @Test fun earlyIdleNeverDropsWaitingStoryReaction() {
        controller.rules = MoodRules(dwellMs = 10_000)
        controller.submit(intent(Expression.HAPPY), 0)                        // plain, held until 3000
        assertEquals(Outcome.QUEUED, controller.submit(intent(Expression.SAD), 1_000, spokenMs = 2_000))
        assertFalse(controller.tick(3_000))                                   // not idle: SAD is waiting
        assertTrue(controller.tick(10_000))
        assertEquals(Expression.SAD, controller.state.active?.expression)
    }

    @Test fun storyNeutralDropsOlderWaitingReaction() {
        controller.rules = MoodRules(dwellMs = 5_000)
        controller.submit(intent(Expression.HAPPY), 0, spokenMs = 1_200)
        controller.submit(intent(Expression.SAD), 1_200, spokenMs = 1_200)
        assertEquals(Outcome.CARRIED, controller.submit(intent(Expression.NONE), 2_400, spokenMs = 1_200))
        assertNull(controller.state.pending)
        controller.tick(5_000)
        assertFalse(sink.calls.contains("expr:SAD"))
    }

    @Test fun storyLookRequestsDoNotExtendMood() {
        controller.submit(intent(Expression.SAD), 0, spokenMs = 2_000)        // carry until 6000
        controller.submit(intent(Expression.NEUTRAL, Look.LEFT), 2_000, spokenMs = 2_000)
        controller.submit(intent(Expression.NEUTRAL, Look.RIGHT), 4_000, spokenMs = 5_000)
        assertEquals(Expression.SAD, controller.state.active?.expression)
        assertFalse(controller.tick(5_999))
        assertTrue(controller.tick(6_000))
        assertNull(controller.state.active)
    }

    @Test fun droppedPendingIsNeverShown() {
        controller.submit(intent(Expression.HAPPY), 0, spokenMs = 1_200)
        assertEquals(Outcome.QUEUED, controller.submit(intent(Expression.SAD), 1_200, spokenMs = 1_200))
        controller.dropPending()
        controller.tick(1_500)
        assertFalse(sink.calls.contains("expr:SAD"))
    }

    @Test fun repeatedStoryGazeStillHoldsWithinCarry() {
        controller.submit(intent(Expression.SAD), 0, spokenMs = 2_000)        // carry until 6000
        controller.submit(intent(Expression.NEUTRAL, Look.LEFT), 2_000, spokenMs = 1_200)
        assertEquals(Outcome.CARRIED, controller.submit(intent(Expression.NEUTRAL, Look.LEFT), 3_200, spokenMs = 2_400))
        assertFalse(controller.tick(5_999))
        assertTrue(controller.tick(6_000))
    }
}
