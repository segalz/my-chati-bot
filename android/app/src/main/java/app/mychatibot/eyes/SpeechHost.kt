package app.mychatibot.eyes

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Speaks a line out loud (Stage 8) while the eyes follow it, sentence by sentence:
 * 1. the line is split into sentences ([StoryText]);
 * 2. the eye model decides each sentence ahead ([EyeBrainHost.classifyAhead]);
 * 3. each decided sentence is synthesized on the voice thread (Piper via sherpa-onnx), in the voice
 *    style of its expression ([VoiceTable]) — the model never chooses how the voice sounds;
 * 4. the player thread plays the sentences in order and hands each reaction to the eye controller
 *    the moment its audio starts, held for the real audio length (story mode of [EyeController]).
 *
 * A newer sentence, story or line supersedes this one: the player stops the audio at once and
 * nothing stale reaches the eyes. The voice model lives in the app's private files dir like the
 * eye model, loads once, and can be released under memory pressure.
 */
class SpeechHost private constructor() {

    private class Clip(val samples: FloatArray, val sampleRate: Int, val synthMs: Long)
    private class Ready(val decision: EyeBrainHost.Decision, val clip: Clip?)
    private class Session(val generation: Long, val sentences: List<String>) {
        val ready: List<CompletableFuture<Ready>> = List(sentences.size) { CompletableFuture<Ready>() }
    }

    private val synthThread = HandlerThread("voice-synth").apply { start() }
    private val synth = Handler(synthThread.looper)
    private val player = Executors.newSingleThreadExecutor { Thread(it, "voice-play") }
    @Volatile private var table = VoiceTable.DEFAULT
    @Volatile private var threads = THREADS
    private var voiceDir: File? = null  // synth thread only
    private var tts: OfflineTts? = null  // synth thread only
    private var loadError: String? = null  // synth thread only
    private var track: AudioTrack? = null  // player thread only

    /** Reads the voice table asset and loads the voice model in the background. */
    fun init(context: Context) {
        val app = context.applicationContext
        synth.post {
            val errors = mutableListOf<String>()
            table = runCatching { VoiceTable.parse(app.assets.open(TABLE_ASSET).reader().readText(), errors) }
                .getOrElse { errors += "cannot read $TABLE_ASSET: ${it.message}"; VoiceTable.DEFAULT }
            errors.forEach { Log.w(TAG, "voice table: $it") }
            voiceDir = File(app.filesDir, "voice/$VOICE_DIR")
            ensureLoaded()
        }
    }

    /** Speaks [text] with the eyes following it; replaces anything said or shown before. */
    fun speak(text: String) {
        val sentences = StoryText.split(text)
        if (sentences.isEmpty()) {
            Log.w(TAG, "speak: no sentences")
            return
        }
        val brain = EyeBrainHost.instance
        val session = Session(brain.beginRequest(), sentences)
        Log.i(TAG, "speak: ${sentences.size} sentences, generation ${session.generation}")
        brain.classifyAhead(session.generation, sentences) { i, decision ->
            synth.post { session.ready[i].complete(Ready(decision, synthesize(session, i, decision))) }
        }
        player.execute { play(session) }
    }

    /** Development only (debug channel): voice model thread count; reloads the model. */
    fun setThreads(value: Int) {
        if (value !in 1..8) return
        threads = value
        synth.post {
            tts?.release()
            tts = null
            ensureLoaded()
        }
    }

    /** Frees the voice model and the audio track (e.g. on memory pressure); the next line reloads them. */
    fun release() {
        synth.post {
            tts?.release()
            tts = null
            Log.i(TAG, "voice model released")
        }
        player.execute { releaseTrack() }
    }

    // --- voice thread ---

    private fun ensureLoaded(): Boolean {
        if (tts != null) return true
        val dir = voiceDir ?: return false
        val model = File(dir, MODEL_FILE)
        if (!model.isFile) {
            loadError = "voice model missing: ${model.path}"
            Log.w(TAG, loadError!!)
            return false
        }
        val started = SystemClock.uptimeMillis()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.path,
                    tokens = File(dir, "tokens.txt").path,
                    dataDir = File(dir, "espeak-ng-data").path,
                ),
                numThreads = threads,
                provider = "cpu",
            ),
        )
        tts = runCatching { OfflineTts(config = config) }.getOrElse {
            loadError = "voice model load failed: ${it.message}"
            Log.w(TAG, loadError!!)
            null
        }
        tts?.let {
            loadError = null
            Log.i(TAG, "voice loaded in ${SystemClock.uptimeMillis() - started} ms: ${it.numSpeakers()} speakers, " +
                "${it.sampleRate()} Hz, $threads threads")
        }
        return tts != null
    }

    /** Null when the request is stale or the voice is unavailable (the eyes still react then). */
    private fun synthesize(session: Session, index: Int, decision: EyeBrainHost.Decision): Clip? {
        if (!EyeBrainHost.instance.isCurrent(session.generation) || !ensureLoaded()) return null
        val engine = tts ?: return null
        val style = table.styleFor(expressionOf(decision))
        val speaker = if (style.speaker < engine.numSpeakers()) style.speaker else 0
        val started = SystemClock.uptimeMillis()
        val audio = runCatching { engine.generate(session.sentences[index], speaker, style.speed) }.getOrElse {
            Log.w(TAG, "synthesis failed: ${it.message}")
            return null
        }
        val clip = Clip(audio.samples, audio.sampleRate, SystemClock.uptimeMillis() - started)
        Log.i(TAG, "synth ${index + 1}/${session.sentences.size}: ${clip.synthMs} ms for ${durationMs(clip)} ms " +
            "(speaker $speaker, speed ${style.speed})")
        return clip
    }

    // --- player thread ---

    private fun play(session: Session) {
        val brain = EyeBrainHost.instance
        val isCurrent = { brain.isCurrent(session.generation) }
        val n = session.sentences.size
        for (i in 0 until n) {
            val ready = await(session.ready[i], isCurrent) ?: return stopAudio()
            val sentence = session.sentences[i]
            val clip = ready.clip
            val audioMs = clip?.let(::durationMs) ?: StoryText.spokenMs(sentence)
            val startMs = SystemClock.uptimeMillis()
            val d = ready.decision
            val voice = clip?.let { " | voice ${it.synthMs} ms for $audioMs ms" } ?: " | voice unavailable"
            EyeControllerHost.instance.submitDecision(sentence, d.body, d.error, d.detail + voice, d.source, isCurrent,
                EyeControllerHost.StoryStep(i, n, startMs, audioMs + GAP_MS))
            Log.i(TAG, "speech: ${i + 1}/$n start t=$startMs audio=$audioMs ms")
            val finished = if (clip != null) playClip(clip, isCurrent) else waitUntil(startMs + audioMs, isCurrent)
            if (!finished || !waitUntil(SystemClock.uptimeMillis() + GAP_MS, isCurrent)) return stopAudio()
        }
    }

    /** Waits for a sentence to be decided and synthesized; null when superseded first. */
    private fun await(future: CompletableFuture<Ready>, isCurrent: () -> Boolean): Ready? {
        while (isCurrent()) {
            try {
                return future.get(POLL_MS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                // not ready yet: check whether the line is still current, then keep waiting
            }
        }
        return null
    }

    /** Plays [clip] to the end; false (audio stopped) when superseded meanwhile. */
    private fun playClip(clip: Clip, isCurrent: () -> Boolean): Boolean {
        val out = trackFor(clip.sampleRate) ?: return waitUntil(SystemClock.uptimeMillis() + durationMs(clip), isCurrent)
        val startHead = out.playbackHeadPosition
        out.play()
        val chunk = clip.sampleRate / 20  // 50 ms, so a newer line stops the audio quickly
        val startMs = SystemClock.uptimeMillis()
        var offset = 0
        while (offset < clip.samples.size) {
            if (!isCurrent()) return false
            val count = minOf(chunk, clip.samples.size - offset)
            val written = out.write(clip.samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                // e.g. ERROR_DEAD_OBJECT after an audio service restart: drop the track (the next
                // sentence builds a new one) and keep the eyes on the clip's timing.
                Log.w(TAG, "audio write failed ($written); recreating the track for the next sentence")
                releaseTrack()
                return waitUntil(startMs + durationMs(clip), isCurrent)
            }
            if (written == 0) break
            offset += written
        }
        // Wait until the last sample has actually been played, not just queued.
        val deadline = SystemClock.uptimeMillis() + durationMs(clip) + 1_000
        while (out.playbackHeadPosition - startHead < offset && SystemClock.uptimeMillis() < deadline) {
            if (!isCurrent()) return false
            SystemClock.sleep(10)
        }
        return true
    }

    private fun trackFor(sampleRate: Int): AudioTrack? {
        track?.let { if (it.sampleRate == sampleRate) return it }
        track?.release()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBytes, sampleRate / 5 * 4))  // ≥ 200 ms of float samples
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrElse {
            Log.w(TAG, "audio track failed: ${it.message}")
            null
        }
        return track
    }

    /** Stops at once. The track is released, not reused: a fresh one has a clean playback position. */
    private fun stopAudio() {
        track?.let {
            it.pause()
            it.flush()
        }
        releaseTrack()
        Log.i(TAG, "speech stopped (superseded)")
    }

    private fun releaseTrack() {
        track?.release()
        track = null
    }

    /** Sleeps until [timeMs] (uptime); false when superseded first. */
    private fun waitUntil(timeMs: Long, isCurrent: () -> Boolean): Boolean {
        while (SystemClock.uptimeMillis() < timeMs) {
            if (!isCurrent()) return false
            SystemClock.sleep(minOf(POLL_MS, timeMs - SystemClock.uptimeMillis()).coerceAtLeast(1))
        }
        return isCurrent()
    }

    private fun durationMs(clip: Clip) = clip.samples.size * 1_000L / clip.sampleRate

    private fun expressionOf(decision: EyeBrainHost.Decision): Expression? {
        val body = decision.body ?: return null
        val name = runCatching { StrictJson.parseFlatObject(body)["expression"] as? String }.getOrNull()
        return Expression.entries.firstOrNull { it.name == name }
    }

    companion object {
        private const val TAG = "ChatiVoice"
        const val VOICE_DIR = "vits-piper-en_US-libritts_r-medium"
        private const val MODEL_FILE = "en_US-libritts_r-medium.onnx"
        private const val TABLE_ASSET = "voice.txt"
        private const val THREADS = 2  // fewer threads leave the CPU to the eye model running at the same time (8.3)
        private const val GAP_MS = 250L  // pause between sentences
        private const val POLL_MS = 100L

        val instance: SpeechHost by lazy { SpeechHost() }
    }
}
