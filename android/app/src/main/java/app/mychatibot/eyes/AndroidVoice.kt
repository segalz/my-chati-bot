package app.mychatibot.eyes

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hebrew voice from the phone's own speech engine (Google TTS, already installed): free and, with an
 * installed Hebrew voice, offline. Each sentence is synthesized to a file and returned as samples, so
 * it plays through the same sentence pipeline as Piper (eyes start with each sentence's audio).
 * Emotion is speech rate and pitch from [VoiceTable]. [synthesize] blocks: call it from one worker
 * thread only.
 */
class AndroidVoice(context: Context) {

    private val app = context.applicationContext
    private class Engine(app: Context) {
        val initialized = CountDownLatch(1)
        @Volatile var ok = false
        val tts = TextToSpeech(app, { status ->
            ok = status == TextToSpeech.SUCCESS
            initialized.countDown()
        }, ENGINE)
    }
    @Volatile private var engine = Engine(app)
    private val tts get() = engine.tts
    private var attempts = 1  // worker thread only
    private var voice: Voice? = null  // worker thread only
    private var nextId = 0L

    /**
     * Waits for the engine and picks a Hebrew voice (offline preferred); false when there is none.
     * A failed engine start is retried a few times (worker thread), not given up for the process.
     */
    fun ready(): Boolean {
        if (voice != null) return true
        val e = engine
        if (!e.initialized.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) || !e.ok) {
            Log.w(TAG, "android voice: engine not ready (attempt $attempts)")
            if (attempts < MAX_INIT_ATTEMPTS) {
                attempts++
                e.tts.shutdown()
                engine = Engine(app)
            }
            return false
        }
        val hebrew = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "iw" || it.locale.language == "he" }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
        Log.i(TAG, "android voice: Hebrew voices: " + hebrew.joinToString { "${it.name}(q${it.quality}${if (it.isNetworkConnectionRequired) ",net" else ""})" })
        val pick = hebrew.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
            ?: hebrew.maxByOrNull { it.quality }
        if (pick == null) {
            Log.w(TAG, "android voice: no Hebrew voice installed")
            return false
        }
        if (tts.setVoice(pick) != TextToSpeech.SUCCESS &&
            tts.setLanguage(Locale("iw", "IL")) < TextToSpeech.LANG_AVAILABLE) {
            Log.w(TAG, "android voice: cannot select ${pick.name}")
            return false
        }
        voice = pick
        Log.i(TAG, "android voice: using ${pick.name}${if (pick.isNetworkConnectionRequired) " (needs network)" else " (offline)"}")
        return true
    }

    /** Speaks [text] into samples at [rate] and [pitch] (1.0 = normal); null on failure or timeout. */
    fun synthesize(text: String, rate: Float, pitch: Float): WavPcm.Audio? {
        if (!ready()) return null
        val id = "say-${nextId++}"
        val file = File(app.cacheDir, "android-voice.wav")
        val done = CountDownLatch(1)
        var ok = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) {
                    ok = true
                    done.countDown()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.countDown()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.countDown()
            }
        })
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        file.delete()
        if (tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) return null
        if (!done.await(SYNTH_TIMEOUT_MS, TimeUnit.MILLISECONDS) || !ok) {
            Log.w(TAG, "android voice: synthesis failed or timed out")
            return null
        }
        return runCatching { WavPcm.parse(file.readBytes()) }.getOrNull()
    }

    fun shutdown() = tts.shutdown()

    companion object {
        private const val TAG = "ChatiVoice"
        private const val ENGINE = "com.google.android.tts"
        private const val INIT_TIMEOUT_MS = 5_000L
        private const val MAX_INIT_ATTEMPTS = 3
        private const val SYNTH_TIMEOUT_MS = 15_000L
    }
}
