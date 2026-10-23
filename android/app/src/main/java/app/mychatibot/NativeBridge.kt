package app.mychatibot

import android.view.Surface
import java.util.concurrent.atomic.AtomicInteger

/** JNI entry points of libchati_eyes: the original espp expressive_eyes engine + Monochrome Blue drawer. */
object NativeBridge {
    init {
        System.loadLibrary("chati_eyes")
    }

    /** espp::ExpressiveEyes::Expression ordinals, in upstream declaration order. */
    val EXPRESSIONS = listOf(
        "NEUTRAL", "HAPPY", "SAD", "ANGRY", "SURPRISED", "SLEEPY", "BORED", "WINK_LEFT", "WINK_RIGHT",
    )

    private val nextSurfaceOwner = AtomicInteger(1)

    /** Unique id per SurfaceView, so a destroyed old surface cannot detach a newer one. */
    fun newSurfaceOwner(): Int = nextSurfaceOwner.getAndIncrement()

    external fun nativeVersion(): String
    external fun nativeStart()
    external fun nativeAttachSurface(surface: Surface, owner: Int)
    external fun nativeDetachSurface(owner: Int)
    external fun nativeSetExpression(id: Int)
    external fun nativeLookAt(x: Float, y: Float)
    external fun nativeBlink()
    external fun nativeStats(): String

    /** Eye model (Stage 6). Returns null on success, else the error. */
    external fun nativeBrainLoad(modelPath: String, threads: Int): String?
    external fun nativeBrainRelease()
    external fun nativeBrainSetThreads(threads: Int, threadsBatch: Int)
    /** Thread-safe: inferences for generations below [generation] stop at their next step. */
    external fun nativeBrainCancelOlderThan(generation: Long)
    /**
     * Tab-separated: OK|ERR, json-or-error, prompt_tokens, sampled_tokens, prompt_ms, generate_ms, total_ms,
     * none_gap. With [continueStory], earlier sentences inferred with the same [generation] stay in
     * the model's context (bounded window); otherwise the context starts empty.
     */
    external fun nativeBrainInfer(sentence: String, timeoutMs: Int, generation: Long, continueStory: Boolean): String
}
