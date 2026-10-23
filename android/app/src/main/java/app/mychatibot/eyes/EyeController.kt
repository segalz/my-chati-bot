package app.mychatibot.eyes

/** Where controller decisions go (the native engine in the app, a fake in tests). */
interface EyeSink {
    fun setExpression(engineId: Int)
    fun lookAt(x: Float, y: Float)
}

/** What the controller did with an intent, for the overlay and logs. */
enum class Outcome {
    APPLIED, QUEUED, IGNORED_NO_REACTION, IGNORED_NO_CHANGE, REJECTED_EXPIRED,
    /** Story: the same reaction again; it simply stays longer. */
    EXTENDED,
    /** Story: a neutral sentence kept the current mood (within the carry limit). */
    CARRIED,
}

/**
 * Pacing for reactions inside a story or conversation (see [EyeController.submit] with a spoken
 * time). Deterministic and owned by the app, never by the model.
 */
data class MoodRules(
    /** A reaction stays this long after its sentence has been spoken. */
    val lingerMs: Long = 1_500,
    /** Neutral sentences keep a mood at most this long after the mood's own sentence ended. */
    val carryMs: Long = 6_000,
    /** A story reaction is shown at least this long before another one may replace it. */
    val dwellMs: Long = 1_500,
)

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
 *
 * Story mode (an intent submitted with the time its sentence takes to say), per [rules]:
 * - a reaction holds for its sentence plus [MoodRules.lingerMs]; the same reaction again only
 *   extends it, so a mood that continues does not restart;
 * - a neutral or no-reaction sentence keeps the current mood, but never longer than
 *   [MoodRules.carryMs] after the mood's last sentence, then the eyes return to idle;
 * - a look request without an emotion turns the eyes but keeps the current mood (not extending it);
 * - a neutral or look sentence drops an older reaction still waiting for its dwell;
 * - a different reaction waits until the current one has been shown [MoodRules.dwellMs].
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
    /** Story mode only: end of the pending intent's sentence (null = plain pending intent). */
    private var pendingSentenceEndMs: Long? = null
    /** Story mode only: neutral sentences may keep the current mood until this time. */
    private var carryUntilMs = 0L

    /** Story pacing; may be changed between submissions (debug channel). */
    var rules = MoodRules()

    val state: State get() = State(active, appliedAtMs, holdUntilMs, pending)

    /** Drops a waiting intent (e.g. it belongs to a story that has been superseded). */
    fun dropPending() {
        pending = null
        pendingSentenceEndMs = null
    }

    /**
     * [spokenMs] is how long the intent's sentence takes to say when it is part of a story or
     * conversation (story mode); null for a single sentence or a debug intent.
     */
    fun submit(intent: EyeIntent, nowMs: Long, spokenMs: Long? = null): Outcome {
        if (isExpired(intent, nowMs)) return Outcome.REJECTED_EXPIRED
        if (spokenMs != null) return submitInStory(intent, nowMs, saturatingAdd(nowMs, maxOf(spokenMs, 0)))
        if (intent.expression == Expression.NONE) return Outcome.IGNORED_NO_REACTION
        if (intent.expression == Expression.NEUTRAL && intent.look == Look.CENTER) return Outcome.IGNORED_NO_CHANGE
        if (active != null && nowMs - appliedAtMs < minDwellMs) {
            pending = intent
            pendingSentenceEndMs = null
            return Outcome.QUEUED
        }
        apply(intent, nowMs)
        return Outcome.APPLIED
    }

    /** Call periodically; returns true when the eyes changed. */
    fun tick(nowMs: Long): Boolean {
        val waiting = pending
        val waitingSentenceEnd = pendingSentenceEndMs
        val dwell = if (waitingSentenceEnd != null) rules.dwellMs else minDwellMs
        if (waiting != null && nowMs - appliedAtMs >= dwell) {
            pending = null
            pendingSentenceEndMs = null
            if (!isExpired(waiting, nowMs)) {
                if (waitingSentenceEnd != null) applyInStory(waiting, nowMs, waitingSentenceEnd) else apply(waiting, nowMs)
                return true
            }
        }
        // Never go idle while something waits: it is shown once the dwell has passed.
        if (active != null && pending == null && nowMs >= holdUntilMs) {
            returnToIdle()
            return true
        }
        return false
    }

    private fun submitInStory(intent: EyeIntent, nowMs: Long, sentenceEndMs: Long): Outcome {
        val shown = active
        val noReaction = intent.expression == Expression.NONE ||
            (intent.expression == Expression.NEUTRAL && intent.look == Look.CENTER)
        if (noReaction || (intent.expression == Expression.NEUTRAL && shown != null)) {
            // The newest sentence says "no new emotion": an older waiting reaction is dropped.
            pending = null
            pendingSentenceEndMs = null
        }
        if (noReaction) {
            val ignored = if (intent.expression == Expression.NONE) Outcome.IGNORED_NO_REACTION else Outcome.IGNORED_NO_CHANGE
            if (shown == null || nowMs >= carryUntilMs) return ignored
            holdUntilMs = maxOf(holdUntilMs, minOf(sentenceEndMs, carryUntilMs))
            return Outcome.CARRIED
        }
        if (intent.expression == Expression.NEUTRAL && shown != null) {
            // A look request without an emotion turns the eyes now and keeps the mood showing, within
            // the mood's own carry limit (it never extends the mood).
            holdUntilMs = maxOf(holdUntilMs, minOf(saturatingAdd(sentenceEndMs, rules.lingerMs), carryUntilMs))
            if (shown.look == intent.look) return Outcome.CARRIED
            sink.lookAt(intent.look.x, intent.look.y)
            active = shown.copy(look = intent.look)
            return Outcome.APPLIED
        }
        val target = intent
        if (shown != null && shown.expression == target.expression && shown.look == target.look) {
            pending = null  // the newest decision is "stay like this"
            pendingSentenceEndMs = null
            holdUntilMs = maxOf(holdUntilMs, saturatingAdd(sentenceEndMs, rules.lingerMs))
            carryUntilMs = maxOf(carryUntilMs, saturatingAdd(sentenceEndMs, rules.carryMs))
            return Outcome.EXTENDED
        }
        if (shown != null && nowMs - appliedAtMs < rules.dwellMs) {
            pending = target
            pendingSentenceEndMs = sentenceEndMs
            return Outcome.QUEUED
        }
        applyInStory(target, nowMs, sentenceEndMs)
        return Outcome.APPLIED
    }

    private fun apply(intent: EyeIntent, nowMs: Long) {
        show(intent, nowMs)
        // Never shorter than the dwell, so a waiting intent is never dropped by an early idle.
        holdUntilMs = saturatingAdd(nowMs, maxOf(intent.holdMs ?: defaultHoldMs, minDwellMs))
        carryUntilMs = 0L
    }

    private fun applyInStory(intent: EyeIntent, nowMs: Long, sentenceEndMs: Long) {
        show(intent, nowMs)
        // Never shorter than the story dwell, so a waiting intent is never dropped by an early idle.
        holdUntilMs = maxOf(saturatingAdd(sentenceEndMs, rules.lingerMs), saturatingAdd(nowMs, rules.dwellMs))
        carryUntilMs = saturatingAdd(sentenceEndMs, rules.carryMs)
    }

    private fun show(intent: EyeIntent, nowMs: Long) {
        sink.setExpression(requireNotNull(intent.expression.engineId))
        sink.lookAt(intent.look.x, intent.look.y)
        active = intent
        pending = null  // anything waiting is older than what is shown now
        pendingSentenceEndMs = null
        appliedAtMs = nowMs
    }

    private fun returnToIdle() {
        sink.setExpression(Expression.NEUTRAL.ordinal)
        sink.lookAt(0f, 0f)
        active = null
        pending = null
        pendingSentenceEndMs = null
        carryUntilMs = 0L
    }

    private fun isExpired(intent: EyeIntent, nowMs: Long) =
        intent.expiresAtMs != null && nowMs >= intent.expiresAtMs

    private fun saturatingAdd(a: Long, b: Long) = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
