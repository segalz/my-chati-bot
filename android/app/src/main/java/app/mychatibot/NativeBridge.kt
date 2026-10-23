package app.mychatibot

import android.view.Surface

/** JNI entry points of libchati_eyes: the original espp expressive_eyes engine + Monochrome Blue drawer. */
object NativeBridge {
    init {
        System.loadLibrary("chati_eyes")
    }

    /** espp::ExpressiveEyes::Expression ordinals, in upstream declaration order. */
    val EXPRESSIONS = listOf(
        "NEUTRAL", "HAPPY", "SAD", "ANGRY", "SURPRISED", "SLEEPY", "BORED", "WINK_LEFT", "WINK_RIGHT",
    )

    external fun nativeVersion(): String
    external fun nativeStart()
    external fun nativeSetSurface(surface: Surface?)
    external fun nativeSetExpression(id: Int)
    external fun nativeLookAt(x: Float, y: Float)
    external fun nativeBlink()
    external fun nativeStats(): String
}
