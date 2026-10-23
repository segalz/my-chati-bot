package app.mychatibot.eyes

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import app.mychatibot.NativeBridge

/**
 * Runs [EyeController] on its own thread (single owner) and feeds the native engine.
 * One instance per process ([instance]), like the native renderer, so an activity recreated by
 * rotation does not lose an active reaction. Inputs arrive as JSON from any thread; every step
 * is reported to the current status listener (called on the controller thread).
 *
 * All controller state, including whether the ticker runs, is only touched on the handler thread.
 */
class EyeControllerHost private constructor() {

    private class Listener(val owner: Any, val onStatus: (String) -> Unit)

    private val thread = HandlerThread("eye-controller").apply { start() }
    private val handler = Handler(thread.looper)
    private val controller = EyeController(object : EyeSink {
        override fun setExpression(engineId: Int) = NativeBridge.nativeSetExpression(engineId)
        override fun lookAt(x: Float, y: Float) = NativeBridge.nativeLookAt(x, y)
    })
    private var listener: Listener? = null  // handler thread only
    private var ticking = false  // handler thread only
    /** Whether the intent waiting in the controller still belongs to the current request (handler thread only). */
    private var pendingIsCurrent: (() -> Boolean)? = null
    private var mode = "idle"
    private var lastInput = "-"
    private var lastResult = "-"

    private val ticker = object : Runnable {
        override fun run() {
            if (!ticking) return
            if (pendingIsCurrent?.invoke() == false) {
                controller.dropPending()
                pendingIsCurrent = null
                Log.i(TAG, "stale waiting reaction dropped")
            }
            if (controller.tick(SystemClock.uptimeMillis())) publish()
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Status goes to [onStatus] until [clearStatusListener] is called with the same [owner]. */
    fun setStatusListener(owner: Any, onStatus: (String) -> Unit) {
        handler.post {
            listener = Listener(owner, onStatus)
            publish()
        }
    }

    /** Clears the listener only if [owner] still owns it (a newer activity may have replaced it). */
    fun clearStatusListener(owner: Any) {
        handler.post { if (listener?.owner === owner) listener = null }
    }

    /** Starts the hold/dwell ticker (call from onResume). */
    fun resume() = handler.post {
        if (!ticking) {
            ticking = true
            handler.post(ticker)
        }
    }

    /** Stops the ticker while the eyes are not visible (call from onPause). */
    fun pause() = handler.post {
        ticking = false
        handler.removeCallbacks(ticker)
    }

    /** Validates and submits a raw intent (debug channel now, the eye model in Stage 6). */
    fun submitJson(json: String, source: IntentSource) = handler.post {
        val now = SystemClock.uptimeMillis()
        lastInput = "${source.name.lowercase()}: ${json.take(120)}"
        lastResult = when (val parsed = EyeIntentParser.parse(json, source, now)) {
            is ParseResult.Rejected -> "REJECTED ${parsed.reason}"
            is ParseResult.Valid -> {
                val outcome = controller.submit(parsed.intent, now)
                if (outcome == Outcome.QUEUED) pendingIsCurrent = null
                if (outcome == Outcome.APPLIED || outcome == Outcome.QUEUED) mode = source.name.lowercase()
                "$outcome ${parsed.intent.expression} ${parsed.intent.look.name.lowercase()}"
            }
        }
        publish()
    }

    /**
     * A decision for [sentence] from the eye model or the phrasebook: [body] is the bare
     * `{"expression":…,"look":…}` and goes through the same strict validator as any input (as a
     * non-DEBUG intent, which may not set timing); on failure the eyes are left as they are and only
     * the status shows the [error]. A decision for a sentence that has since been superseded
     * ([isCurrent] false on this thread) is dropped.
     */
    fun submitDecision(
        sentence: String, body: String?, error: String?, detail: String, source: IntentSource,
        isCurrent: () -> Boolean, story: StoryStep? = null,
    ) {
        val run = Runnable { decide(sentence, body, error, detail, source, isCurrent, story) }
        if (story == null) handler.post(run) else handler.postAtTime(run, story.startAtMs)
    }

    /** Story pacing for the controller (debug channel); null keeps a value. */
    fun setMoodRules(lingerMs: Long?, carryMs: Long?, dwellMs: Long?) = handler.post {
        val r = controller.rules
        controller.rules = MoodRules(
            lingerMs?.takeIf { it in 0..10_000 } ?: r.lingerMs,
            carryMs?.takeIf { it in 0..20_000 } ?: r.carryMs,
            dwellMs?.takeIf { it in 0..10_000 } ?: r.dwellMs,
        )
        Log.i(TAG, "mood rules: ${controller.rules}")
    }

    private fun decide(
        sentence: String, body: String?, error: String?, detail: String, source: IntentSource,
        isCurrent: () -> Boolean, story: StoryStep?,
    ) {
        if (!isCurrent()) {
            Log.i(TAG, "stale ${source.name.lowercase()} result for '${sentence.take(60)}' dropped")
            return
        }
        val now = SystemClock.uptimeMillis()
        val who = source.name.lowercase()
        val step = story?.let { " | story ${it.index + 1}/${it.count}" } ?: ""
        if (story != null) {
            Log.i(TAG, "story: ${story.index + 1}/${story.count} start t=$now planned=${story.startAtMs} " +
                "spoken=${story.spokenMs} '${sentence.take(120)}'")
        }
        lastInput = "say: ${sentence.take(120)}"
        lastResult = if (body == null) {
            "$error | $who $detail$step"
        } else when (val parsed = EyeIntentParser.parse(EyeIntentParser.wrapModelOutput(body), source, now)) {
            is ParseResult.Rejected -> "${who.uppercase()} OUTPUT REJECTED ${parsed.reason} | $body | $who $detail$step"
            is ParseResult.Valid -> {
                val outcome = controller.submit(parsed.intent, now, story?.spokenMs)
                if (outcome == Outcome.QUEUED) pendingIsCurrent = isCurrent
                if (outcome == Outcome.APPLIED || outcome == Outcome.QUEUED) mode = who
                "$outcome ${parsed.intent.expression} ${parsed.intent.look.name.lowercase()} | $who $detail$step"
            }
        }
        publish()
    }

    private fun publish() {
        val s = controller.state
        if (s.active == null) mode = "idle"
        val applied = s.active?.let { "${it.expression} ${it.look.name.lowercase()}" } ?: "NEUTRAL center (idle)"
        val pending = s.pending?.let { " | pending ${it.expression}" } ?: ""
        val text = "mode=$mode\nin: $lastInput\nresult: $lastResult\napplied: $applied$pending"
        Log.i(TAG, "t=${SystemClock.uptimeMillis()} " + text.replace('\n', ' '))
        listener?.onStatus?.invoke(text)
    }

    /** Sentence [index] of [count] in a story: shown at [startAtMs] (uptime), spoken for [spokenMs]. */
    data class StoryStep(val index: Int, val count: Int, val startAtMs: Long, val spokenMs: Long)

    companion object {
        private const val TAG = "ChatiEyes"
        private const val TICK_MS = 100L

        val instance: EyeControllerHost by lazy { EyeControllerHost() }
    }
}
