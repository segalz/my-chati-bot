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
    private var audio: AudioOut? = null  // player thread only: the stream of the line being spoken

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
        player.execute {
            audio?.close()
            audio = null
        }
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

    /**
     * One audio stream for a whole line (player thread only). It is kept fed — with a short silent
     * warm-up before the first sentence, silence between sentences and while waiting for the next
     * one — because an output that runs dry is disabled and the device's speaker path goes to
     * standby: the next sentence then starts while the speaker is still waking up, and its beginning
     * is lost (seen on the phone: only the end of a line was heard). Reactions are started by the
     * playback position, i.e. when their sentence actually starts playing, not when it was queued.
     */
    private class AudioOut(private val track: AudioTrack, val sampleRate: Int) {
        enum class Result { OK, STALE, FAILED }

        private var written = 0L  // frames queued since play()
        private val chunk = sampleRate / 20  // 50 ms, so a newer line stops the audio quickly
        private var lastHead = 0L
        private var wraps = 0L

        /** Frames played since play(); the platform's 32-bit counter extended across wrap-around. */
        private fun played(): Long {
            val head = track.playbackHeadPosition.toLong() and 0xffff_ffffL
            if (head < lastHead) wraps++
            lastHead = head
            return (wraps shl 32) + head
        }

        fun start(isCurrent: () -> Boolean): Result {
            track.play()
            return silence(WARMUP_MS, isCurrent)
        }

        fun silence(ms: Long, isCurrent: () -> Boolean): Result =
            feed(FloatArray((sampleRate * ms / 1_000).toInt()), isCurrent, null)

        /** Queues [samples]; [onStart] runs when playback reaches their first sample. */
        fun feed(samples: FloatArray, isCurrent: () -> Boolean, onStart: (() -> Unit)?): Result {
            val startFrame = written
            var started = onStart == null
            var offset = 0
            while (offset < samples.size) {
                if (!isCurrent()) return Result.STALE
                if (!started && played() >= startFrame) {
                    onStart?.invoke()
                    started = true
                }
                val count = minOf(chunk, samples.size - offset)
                val n = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                if (n < 0) {
                    Log.w(TAG, "audio write failed ($n)")
                    return Result.FAILED
                }
                if (n == 0) return Result.FAILED
                offset += n
                written += n
            }
            val deadline = SystemClock.uptimeMillis() + BUFFER_MS + 1_000
            while (!started) {
                if (!isCurrent()) return Result.STALE
                if (played() >= startFrame || SystemClock.uptimeMillis() > deadline) {
                    onStart?.invoke()
                    started = true
                } else {
                    SystemClock.sleep(5)
                }
            }
            return Result.OK
        }

        /** Waits until everything queued has been played. */
        fun drain(isCurrent: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + BUFFER_MS + 1_000
            while (played() < written && SystemClock.uptimeMillis() < deadline && isCurrent()) SystemClock.sleep(10)
        }

        fun close() {
            track.pause()
            track.flush()
            track.release()
        }

        companion object {
            fun open(sampleRate: Int): AudioOut? {
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val minBytes = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                )
                val track = runCatching {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(maxOf(minBytes, (sampleRate * BUFFER_MS / 1_000).toInt() * 4))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }.getOrElse {
                    Log.w(TAG, "audio track failed: ${it.message}")
                    return null
                }
                return AudioOut(track, sampleRate)
            }
        }
    }

    private fun play(session: Session) {
        val brain = EyeBrainHost.instance
        val isCurrent = { brain.isCurrent(session.generation) }
        val n = session.sentences.size
        try {
            for (i in 0 until n) {
                val ready = await(session.ready[i], isCurrent) ?: return stopped()
                val sentence = session.sentences[i]
                val clip = ready.clip
                val audioMs = clip?.let(::durationMs) ?: StoryText.spokenMs(sentence)
                val d = ready.decision
                val voice = clip?.let { " | voice ${it.synthMs} ms for $audioMs ms" } ?: " | voice unavailable"
                var startedAtMs = 0L
                val onStart: () -> Unit = {
                    if (startedAtMs == 0L) {  // exactly once per sentence, whatever happens to the audio
                        startedAtMs = SystemClock.uptimeMillis()
                        EyeControllerHost.instance.submitDecision(sentence, d.body, d.error, d.detail + voice, d.source,
                            isCurrent, EyeControllerHost.StoryStep(i, n, startedAtMs, audioMs + GAP_MS))
                        Log.i(TAG, "speech: ${i + 1}/$n start t=$startedAtMs audio=$audioMs ms")
                    }
                }
                if (clip != null && audio?.sampleRate != clip.sampleRate) {
                    audio?.close()
                    audio = AudioOut.open(clip.sampleRate)
                    when (audio?.start(isCurrent)) {
                        AudioOut.Result.STALE -> return stopped()
                        AudioOut.Result.FAILED -> dropAudio()
                        else -> Unit
                    }
                }
                val out = audio
                val result = when {
                    clip != null && out != null -> out.feed(clip.samples, isCurrent, onStart).let { r ->
                        if (r == AudioOut.Result.OK) out.silence(GAP_MS, isCurrent) else r
                    }
                    // No voice for this sentence: the eyes keep its timing; an open stream is kept fed.
                    out != null -> {
                        onStart()
                        out.silence(audioMs + GAP_MS, isCurrent)
                    }
                    else -> {
                        onStart()
                        if (waitUntil(startedAtMs + audioMs + GAP_MS, isCurrent)) AudioOut.Result.OK
                        else AudioOut.Result.STALE
                    }
                }
                when (result) {
                    AudioOut.Result.STALE -> return stopped()
                    AudioOut.Result.FAILED -> {
                        // e.g. ERROR_DEAD_OBJECT after an audio service restart: drop the stream (the
                        // next sentence opens a new one); the eyes still get this sentence, on time.
                        dropAudio()
                        onStart()
                        if (!waitUntil(startedAtMs + audioMs + GAP_MS, isCurrent)) return stopped()
                    }
                    AudioOut.Result.OK -> Unit
                }
            }
            audio?.drain(isCurrent)
        } finally {
            audio?.close()
            audio = null
        }
    }

    /**
     * Waits for a sentence to be decided and synthesized, keeping an open stream fed with silence
     * meanwhile; null when superseded first.
     */
    private fun await(future: CompletableFuture<Ready>, isCurrent: () -> Boolean): Ready? {
        while (isCurrent()) {
            future.getNow(null)?.let { return it }
            val out = audio
            if (out != null) {
                if (out.silence(SILENCE_STEP_MS, isCurrent) == AudioOut.Result.FAILED) dropAudio()
            } else {
                try {
                    return future.get(POLL_MS, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    // not ready yet: check whether the line is still current, then keep waiting
                }
            }
        }
        return null
    }

    private fun dropAudio() {
        audio?.close()
        audio = null
    }

    private fun stopped() {
        Log.i(TAG, "speech stopped (superseded)")
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
        private const val WARMUP_MS = 300L  // silence before the first sentence: wakes the speaker path
        private const val SILENCE_STEP_MS = 50L  // silence fed while waiting for the next sentence
        private const val BUFFER_MS = 250L  // audio buffer; bounds how far silence runs ahead

        val instance: SpeechHost by lazy { SpeechHost() }
    }
}
