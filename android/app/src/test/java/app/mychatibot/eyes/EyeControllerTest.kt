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
        override fun setShape(id: Int) { calls += "shape:$id" }
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

    @Test fun idleGlancesCanBeDisabledAtRuntime() {
        val c = glancer(Look.RIGHT)
        c.tick(0)
        c.idleGlances = false
        assertFalse(c.tick(15_000))
        assertFalse(c.tick(30_000))
        assertTrue(sink.calls.isEmpty())
        c.idleGlances = true
        assertFalse(c.tick(30_100))
        assertFalse(c.tick(45_099))
        assertTrue(c.tick(45_100))
        assertEquals(listOf("look:1.0,0.0"), sink.calls)
    }

    @Test fun disablingDuringIdleGlanceRecentersImmediately() {
        val c = glancer(Look.RIGHT)
        c.tick(0)
        assertTrue(c.tick(15_000))
        c.idleGlances = false
        assertTrue(c.tick(15_001))
        assertEquals(listOf("look:1.0,0.0", "look:0.0,0.0"), sink.calls)
        assertFalse(c.tick(16_000))
    }

    @Test fun disablingIdleGlancesDoesNotCancelExplicitGlance() {
        val c = glancer()
        c.idleGlances = false
        assertTrue(c.glance(Look.LEFT, 0))
        assertFalse(c.tick(1))
        assertTrue(c.tick(1_000))
        assertEquals(listOf("look:-1.0,0.0", "look:0.0,0.0"), sink.calls)
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

    @Test fun resetDuringAGlanceRecenters() {
        assertTrue(controller.glance(Look.LEFT, 0))
        controller.reset()
        assertEquals("look:0.0,0.0", sink.calls.last())
        assertFalse(controller.tick(5_000))  // no glance end left to recenter later
    }

    @Test fun resetReturnsToIdleAndDropsPending() {
        controller.submit(intent(Expression.HAPPY), 0)
        controller.submit(intent(Expression.SAD), 100)  // waiting
        controller.reset()
        assertNull(controller.state.active)
        assertNull(controller.state.pending)
        assertFalse(controller.tick(5_000))
        assertFalse(sink.calls.contains("expr:SAD"))
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

    @Test fun thinkingWaitsForDelayAndRecentersWhenDone() {
        val c = EyeController(sink).apply { thinkingEnabled = true }
        c.thinking(true, 0)
        c.thinking(true, 100) // repeated busy notifications do not restart the delay
        assertFalse(c.tick(299))
        assertTrue(c.tick(300))
        assertEquals(listOf("look:-0.5,-0.7"), sink.calls)
        assertFalse(c.tick(400))
        c.thinking(false, 401)
        assertEquals("look:0.0,0.0", sink.calls.last())
        assertFalse(c.tick(500))
    }

    @Test fun quickInferenceNeverShowsThinking() {
        val c = EyeController(sink).apply { thinkingEnabled = true }
        c.thinking(true, 0)
        c.thinking(false, 299)
        assertFalse(c.tick(300))
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun reactionReplacesThinkingWithoutDwellOrLateRecenter() {
        val c = EyeController(sink).apply { thinkingEnabled = true }
        c.thinking(true, 0)
        c.tick(300)
        assertEquals(Outcome.APPLIED, c.submit(intent(Expression.HAPPY, Look.RIGHT), 301))
        val calls = sink.calls.toList()
        c.thinking(false, 302)
        assertEquals(calls, sink.calls)
        assertEquals(listOf("expr:HAPPY", "look:1.0,0.0"), sink.calls.takeLast(2))
    }

    @Test fun thinkingDoesNotReplaceActivePendingOrExplicitGlance() {
        val c = EyeController(sink).apply { thinkingEnabled = true }
        c.submit(intent(Expression.HAPPY), 0)
        c.submit(intent(Expression.SAD), 100)
        c.thinking(true, 0)
        assertFalse(c.tick(300))
        assertTrue(c.tick(800))
        assertFalse(sink.calls.contains("look:-0.5,-0.7"))
        c.reset(900)
        c.glance(Look.LEFT, 900)
        c.thinking(true, 900)
        assertFalse(c.tick(1_200))
        assertFalse(sink.calls.contains("look:-0.5,-0.7"))
        c.tick(2_100) // explicit glance ends first
        assertTrue(c.tick(2_200))
        assertEquals("look:-0.5,-0.7", sink.calls.last())
    }

    @Test fun disablingThinkingRecentersOnlyItsOwnGlance() {
        val c = EyeController(sink).apply { thinkingEnabled = true }
        c.thinking(true, 0)
        c.tick(300)
        c.thinkingEnabled = false
        assertTrue(c.tick(301))
        assertEquals("look:0.0,0.0", sink.calls.last())
        assertFalse(c.tick(1_000))
    }

    @Test fun sleepAfterTwoMinutesAndNoGlancesWhileAsleep() {
        val c = EyeController(sink, sleepAfterMs = 120_000, idleGlanceMs = 15_000).apply {
            thinkingEnabled = true
            idleGlances = false
        }
        assertFalse(c.tick(0))
        assertFalse(c.tick(119_999))
        assertTrue(c.tick(120_000))
        assertTrue(c.sleeping)
        assertEquals(listOf("expr:SLEEPY", "shape:1"), sink.calls)
        c.idleGlances = true
        c.thinking(true, 120_000)
        assertFalse(c.glance(Look.RIGHT, 120_100))
        assertFalse(c.tick(121_000))
        assertEquals(2, sink.calls.size)
    }

    @Test fun inputWakeRestoresOriginalNeutralCenteredAndRestartsQuietTime() {
        val c = EyeController(sink, sleepAfterMs = 120_000)
        c.tick(0)
        c.tick(120_000)
        assertTrue(c.wake(120_001))
        assertFalse(c.sleeping)
        assertEquals(listOf("shape:0", "expr:NEUTRAL", "look:0.0,0.0"), sink.calls.takeLast(3))
        assertFalse(c.wake(120_002))
        assertFalse(c.tick(240_001))
        assertTrue(c.tick(240_002))
    }

    @Test fun noneSubmitWakesAndCountsAsActivity() {
        val c = EyeController(sink, sleepAfterMs = 120_000)
        c.tick(0)
        c.submit(intent(Expression.NONE), 119_999)
        assertFalse(c.tick(120_000))
        assertTrue(c.tick(239_999))
        assertEquals(Outcome.IGNORED_NO_REACTION, c.submit(intent(Expression.NONE), 240_000))
        assertFalse(c.sleeping)
        assertEquals(240_000L, c.lastActivityMs)
    }

    @Test fun reactionSubmitWakesAndAppliesImmediately() {
        val c = EyeController(sink, sleepAfterMs = 120_000)
        c.tick(0)
        c.tick(120_000)
        assertEquals(Outcome.APPLIED, c.submit(intent(Expression.SAD, Look.LEFT), 120_001))
        assertFalse(c.sleeping)
        assertEquals(listOf("shape:0", "expr:NEUTRAL", "look:0.0,0.0", "expr:SAD", "look:-1.0,0.0"), sink.calls.takeLast(5))
    }

    @Test fun resetWakesAndClearsThinking() {
        val c = EyeController(sink, sleepAfterMs = 120_000).apply { thinkingEnabled = true }
        c.tick(0)
        c.tick(120_000)
        c.thinking(true, 120_100)
        c.reset()
        assertFalse(c.sleeping)
        assertTrue(sink.calls.contains("shape:0"))
        assertEquals("look:0.0,0.0", sink.calls.last())
        assertFalse(c.tick(120_400))
    }

    @Test fun glancesDoNotPostponeSleepAndIdleReturnCountsAsActivity() {
        val c = EyeController(sink, sleepAfterMs = 120_000)
        c.submit(intent(Expression.HAPPY), 0)
        c.tick(3_000)
        assertEquals(3_000L, c.lastActivityMs)
        assertTrue(c.glance(Look.RIGHT, 100_000))
        c.tick(101_200)
        assertEquals(3_000L, c.lastActivityMs)
        assertFalse(c.tick(122_999))
        assertTrue(c.tick(123_000))
    }

    @Test fun sleepSwitchOffWakesAndPreventsSleep() {
        val c = EyeController(sink, sleepAfterMs = 120_000)
        c.tick(0)
        c.tick(120_000)
        c.sleepEnabled = false
        assertTrue(c.tick(120_001))
        assertFalse(c.sleeping)
        assertFalse(c.tick(360_000))
        c.sleepEnabled = true
        assertTrue(c.tick(360_001))
        c.sleepAfterMs = 0
        assertTrue(c.tick(360_002))
        assertFalse(c.sleeping)
    }

    @Test fun defaultsKeepThinkingAndSleepOff() {
        controller.thinking(true, 0)
        assertFalse(controller.tick(0))
        assertFalse(controller.tick(300))
        assertFalse(controller.tick(120_000))
        assertFalse(controller.sleeping)
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun disabledThinkingKeepsOldIdleGlances() {
        val c = glancer(Look.RIGHT)
        c.thinking(true, 0)
        assertFalse(c.tick(0))
        assertTrue(c.tick(15_000))
        assertEquals(listOf("look:1.0,0.0"), sink.calls)
        c.thinking(false, 15_001)
        assertEquals(1, sink.calls.size) // ending thinking must not recenter an idle glance
    }

    @Test fun sleepCannotReplaceAnActiveReactionOrPendingIntent() {
        val c = EyeController(sink, sleepAfterMs = 120_000)
        c.rules = MoodRules(dwellMs = 200_000)
        c.submit(intent(Expression.HAPPY, hold = 180_000), 0)
        c.submit(intent(Expression.SAD), 1, spokenMs = 180_000)
        assertFalse(c.tick(120_001))
        assertFalse(c.sleeping)
        assertEquals(Expression.HAPPY, c.state.active?.expression)
        assertEquals(Expression.SAD, c.state.pending?.expression)
    }
}
