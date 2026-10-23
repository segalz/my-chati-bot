package app.mychatibot.eyes

/** Where controller decisions go (the native engine in the app, a fake in tests). */
interface EyeSink {
    fun setExpression(engineId: Int)
    fun lookAt(x: Float, y: Float)
}

/** What the controller did with an intent, for the overlay and logs. */
enum class Outcome { APPLIED, QUEUED, IGNORED_NO_REACTION, IGNORED_NO_CHANGE, REJECTED_EXPIRED }

/**
 * Turns validated [EyeIntent]s into engine commands. Not thread-safe: one owner thread calls
 * [submit] and [tick] (see EyeControllerHost).
 *
 * Rules:
 * - Expired intents are rejected; NONE means "do not react"; NEUTRAL + CENTER changes nothing.
 * - The newest valid intent wins, but a shown reaction is kept at least [minDwellMs] so noisy
 *   input cannot make the eyes twitch; an intent arriving earlier waits (replacing any older
 *   waiting one).
 * - A reaction is held for its holdMs (or the default) and then the eyes return to idle
 *   (NEUTRAL, centered) — the deterministic fallback when nothing new arrives. Blinking and
 *   idle animation stay with the native engine.
 */
class EyeController(
    private val sink: EyeSink,
    private val minDwellMs: Long = 800,
    private val defaultHoldMs: Long = 3_000,
) {
    data class State(
        val active: EyeIntent?,
        val appliedAtMs: Long,
        val holdUntilMs: Long,
        val pending: EyeIntent?,
    )

    private var active: EyeIntent? = null
    private var appliedAtMs = 0L
    private var holdUntilMs = 0L
    private var pending: EyeIntent? = null

    val state: State get() = State(active, appliedAtMs, holdUntilMs, pending)

    fun submit(intent: EyeIntent, nowMs: Long): Outcome {
        if (isExpired(intent, nowMs)) return Outcome.REJECTED_EXPIRED
        if (intent.expression == Expression.NONE) return Outcome.IGNORED_NO_REACTION
        if (intent.expression == Expression.NEUTRAL && intent.look == Look.CENTER) return Outcome.IGNORED_NO_CHANGE
        if (active != null && nowMs - appliedAtMs < minDwellMs) {
            pending = intent
            return Outcome.QUEUED
        }
        apply(intent, nowMs)
        return Outcome.APPLIED
    }

    /** Call periodically; returns true when the eyes changed. */
    fun tick(nowMs: Long): Boolean {
        val waiting = pending
        if (waiting != null && nowMs - appliedAtMs >= minDwellMs) {
            pending = null
            if (!isExpired(waiting, nowMs)) {
                apply(waiting, nowMs)
                return true
            }
        }
        if (active != null && nowMs >= holdUntilMs) {
            returnToIdle()
            return true
        }
        return false
    }

    private fun apply(intent: EyeIntent, nowMs: Long) {
        sink.setExpression(requireNotNull(intent.expression.engineId))
        sink.lookAt(intent.look.x, intent.look.y)
        active = intent
        pending = null  // anything waiting is older than what is shown now
        appliedAtMs = nowMs
        // Never shorter than the dwell, so a waiting intent is never dropped by an early idle.
        val hold = maxOf(intent.holdMs ?: defaultHoldMs, minDwellMs)
        holdUntilMs = if (nowMs > Long.MAX_VALUE - hold) Long.MAX_VALUE else nowMs + hold
    }

    private fun returnToIdle() {
        sink.setExpression(Expression.NEUTRAL.ordinal)
        sink.lookAt(0f, 0f)
        active = null
        pending = null
    }

    private fun isExpired(intent: EyeIntent, nowMs: Long) =
        intent.expiresAtMs != null && nowMs >= intent.expiresAtMs
}
