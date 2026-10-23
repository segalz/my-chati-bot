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
    private val controller = EyeController(sink, minDwellMs = 800, defaultHoldMs = 3_000)

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
}
