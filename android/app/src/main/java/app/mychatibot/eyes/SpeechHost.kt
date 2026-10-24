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
import java.util.concurrent.LinkedBlockingQueue
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
 *
 * Hebrew lines (Stage 9) go to the cloud voice instead ([GeminiVoice]): one streamed request per
 * line, Gemini reads the emotion from the text. The eye model is English-only (R12), so the sender
 * also gives the English version; each English sentence is judged and its reaction starts when the
 * matching Hebrew sentence is reached in the audio ([LineTiming], proportional to its letters).
 */
class SpeechHost private constructor() {

    private class Clip(val samples: FloatArray, val sampleRate: Int, val synthMs: Long)
    private class Ready(val decision: EyeBrainHost.Decision, val clip: Clip?)
    /** [sentences] are what is spoken; [hebrew] lines use the phone's Hebrew voice instead of Piper. */
    private class Session(val generation: Long, val sentences: List<String>, val hebrew: Boolean = false) {
        val ready: List<CompletableFuture<Ready>> = List(sentences.size) { CompletableFuture<Ready>() }
    }

    private val synthThread = HandlerThread("voice-synth").apply { start() }
    private val synth = Handler(synthThread.looper)
    // The phone's Hebrew voice gets its own worker, so its start-up and waits never hold up Piper.
    private val hebrewThread = HandlerThread("voice-hebrew").apply { start() }
    private val hebrewWorker = Handler(hebrewThread.looper)
    @Volatile private var hebrewWarm = false
    private val player = Executors.newSingleThreadExecutor { Thread(it, "voice-play") }
    @Volatile private var table = VoiceTable.DEFAULT
    @Volatile private var threads = THREADS
    private var voiceDir: File? = null  // synth thread only
    private var tts: OfflineTts? = null  // synth thread only
    private var loadError: String? = null  // synth thread only
    private var audio: AudioOut? = null  // player thread only: the stream of the line being spoken
    private val net = Executors.newSingleThreadExecutor { Thread(it, "voice-net") }
    @Volatile private var cloud: GeminiVoice? = null
    @Volatile private var hebrewVoice: AndroidVoice? = null
    /** Which voice speaks Hebrew: the phone's own (free, default) or the Gemini cloud voice. */
    @Volatile private var hebrewEngine = HEBREW_ANDROID

    /**
     * One cloud line in flight: the network thread produces audio into a bounded queue and reports
     * the end (and the total length) directly, not through the queue, so the player knows where the
     * sentences start long before it has played up to them.
     */
    private class CloudLine {
        val call = GeminiVoice.Call()
        val pcm = LinkedBlockingQueue<FloatArray>(QUEUE_CHUNKS)
        @Volatile var ended = false
        @Volatile var totalFrames = 0L  // valid once ended
        @Volatile var error: String? = null  // a fixed category, never server text
        @Volatile var lastAudioAtMs = 0L  // uptime of the latest chunk queued
    }

    /** Reads the voice table asset and loads the voice model in the background. */
    fun init(context: Context) {
        val app = context.applicationContext
        if (hebrewVoice == null) hebrewVoice = AndroidVoice(app)
        synth.post {
            val errors = mutableListOf<String>()
            table = runCatching { VoiceTable.parse(app.assets.open(TABLE_ASSET).reader().readText(), errors) }
                .getOrElse { errors += "cannot read $TABLE_ASSET: ${it.message}"; VoiceTable.DEFAULT }
            errors.forEach { Log.w(TAG, "voice table: $it") }
            voiceDir = File(app.filesDir, "voice/$VOICE_DIR")
            cloud = GeminiVoice(File(app.filesDir, "secrets/$KEY_FILE"))
            ensureLoaded()
        }
        hebrewWorker.post(::warmUpHebrew)
    }

    /**
     * Speaks [text] with the eyes following it; replaces anything said or shown before. Hebrew goes
     * to the cloud voice and needs [english] (the same line in English) for the eyes.
     */
    fun speak(text: String, english: String? = null) {
        val hebrew = LineTiming.isHebrew(text)
        if (hebrew && hebrewEngine == HEBREW_GEMINI) return speakCloud(text, english)
        val sentences = StoryText.split(text)
        if (sentences.isEmpty()) {
            Log.w(TAG, "speak: no sentences")
            return
        }
        val brain = EyeBrainHost.instance
        val session = Session(brain.beginRequest(), sentences, hebrew)
        // R12: the eye model reads English only — a Hebrew line brings its English version.
        val forEyes = if (hebrew) LineTiming.englishFor(sentences, english?.let(StoryText::split).orEmpty()) else sentences
        Log.i(TAG, "speak: ${sentences.size} sentences${if (hebrew) " (Hebrew, phone voice), ${forEyes.size} for the eyes" else ""}, " +
            "generation ${session.generation}")
        val worker = if (hebrew) hebrewWorker else synth
        if (forEyes.isEmpty()) {
            val none = EyeBrainHost.Decision(null, "NO ENGLISH FOR THE EYES", "", IntentSource.MODEL)
            for (i in sentences.indices) worker.post { session.ready[i].complete(Ready(none, synthesize(session, i, none))) }
        } else {
            brain.classifyAhead(session.generation, forEyes) { i, decision ->
                worker.post { session.ready[i].complete(Ready(decision, synthesize(session, i, decision))) }
            }
        }
        player.execute { play(session) }
    }

    /** Development only (debug channel): which voice speaks Hebrew ("android" or "gemini"). */
    fun setHebrewEngine(name: String?) {
        if (name == HEBREW_ANDROID || name == HEBREW_GEMINI) hebrewEngine = name
        Log.i(TAG, "Hebrew voice: $hebrewEngine")
    }

    private fun speakCloud(text: String, english: String?) {
        val sentences = StoryText.split(text)
        if (sentences.isEmpty()) {
            Log.w(TAG, "speak: no sentences")
            return
        }
        val brain = EyeBrainHost.instance
        val session = Session(brain.beginRequest(), sentences)
        val isCurrent = { brain.isCurrent(session.generation) }
        val forEyes = LineTiming.englishFor(sentences, english?.let(StoryText::split).orEmpty())
        Log.i(TAG, "speak (cloud): ${sentences.size} sentences, ${forEyes.size} for the eyes, generation ${session.generation}")
        if (forEyes.isEmpty()) {
            // R12: the eye model only reads English; without it the eyes keep their current state.
            val none = EyeBrainHost.Decision(null, "NO ENGLISH FOR THE EYES", "", IntentSource.MODEL)
            session.ready.forEach { it.complete(Ready(none, null)) }
        } else {
            brain.classifyAhead(session.generation, forEyes) { i, d -> session.ready[i].complete(Ready(d, null)) }
        }
        val line = CloudLine()
        val voice = cloud
        net.execute { produceCloud(text, voice, line, isCurrent) }
        player.execute { playCloud(session, line) }
    }

    /** Network thread: streams the line's audio into [line]; always marks the end exactly once. */
    private fun produceCloud(text: String, voice: GeminiVoice?, line: CloudLine, isCurrent: () -> Boolean) {
        var produced = 0L
        val started = SystemClock.uptimeMillis()
        try {
            when {
                !isCurrent() || line.call.cancelled -> Unit  // superseded before it started
                voice == null || !voice.hasKey() -> line.error = "no Gemini key"
                else -> voice.stream(text, line.call) { chunk ->
                    var queued = false
                    while (!queued && !line.call.cancelled && isCurrent()) {
                        queued = line.pcm.offer(chunk, POLL_MS, TimeUnit.MILLISECONDS)
                    }
                    if (queued) {
                        line.lastAudioAtMs = SystemClock.uptimeMillis()
                        if (produced == 0L) Log.i(TAG, "cloud: first audio after ${SystemClock.uptimeMillis() - started} ms")
                        produced += chunk.size
                    }
                    queued
                }
            }
        } catch (e: GeminiVoice.Failure) {
            line.error = e.message
        } catch (e: Exception) {
            line.error = "internal error"
        } finally {
            line.totalFrames = produced
            line.ended = true
            line.error?.let {
                Log.w(TAG, "cloud voice failed: $it")
                if (isCurrent() && !line.call.cancelled) EyeControllerHost.instance.showError("cloud voice failed", isCurrent)
            }
        }
    }

    /** Development only (debug channel): Gemini voice name and style; null keeps a value. */
    fun setCloudVoice(voice: String?, style: String?) {
        val c = cloud ?: return
        voice?.takeIf { Regex("[A-Za-z]{2,20}").matches(it) }?.let { c.voice = it }
        style?.takeIf { it.length in 1..300 }?.let { c.style = it }
        Log.i(TAG, "cloud voice: ${c.voice}, style '${c.style}'")
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
        if (!EyeBrainHost.instance.isCurrent(session.generation)) return null
        if (session.hebrew) return synthesizeHebrew(session, index, decision)
        if (!ensureLoaded()) return null
        val engine = tts ?: return null
        val style = table.styleFor(expressionOf(decision))
        val speaker = if (style.speaker < engine.numSpeakers()) style.speaker else 0
        val started = SystemClock.uptimeMillis()
        val audio = runCatching { engine.generate(session.sentences[index], speaker, style.speed) }.getOrElse {
            Log.w(TAG, "synthesis failed: ${it.message}")
            return null
        }
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) return null
        val clip = Clip(audio.samples, audio.sampleRate, SystemClock.uptimeMillis() - started)
        Log.i(TAG, "synth ${index + 1}/${session.sentences.size}: ${clip.synthMs} ms for ${durationMs(clip)} ms " +
            "(speaker $speaker, speed ${style.speed})")
        return clip
    }

    /**
     * Hebrew worker, once per process at start-up: starts the phone's speech engine and synthesizes
     * one short word (discarded), so the first real Hebrew line does not pay the engine's start (~3 s)
     * and first synthesis (~4 s) — measured 8.2 s to the first audio without this.
     */
    private fun warmUpHebrew() {
        if (hebrewWarm) return
        val voice = hebrewVoice ?: return
        val started = SystemClock.uptimeMillis()
        val ok = voice.synthesize(WARM_UP_WORD, 1.0f, 1.0f) != null
        hebrewWarm = ok
        Log.i(TAG, "Hebrew voice warm-up ${if (ok) "done" else "failed"} in ${SystemClock.uptimeMillis() - started} ms")
    }

    private fun synthesizeHebrew(session: Session, index: Int, decision: EyeBrainHost.Decision): Clip? {
        val voice = hebrewVoice ?: return null
        val style = table.styleFor(expressionOf(decision))
        val started = SystemClock.uptimeMillis()
        val audio = voice.synthesize(session.sentences[index], style.speed, style.pitch) ?: return null
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) return null
        val clip = Clip(audio.samples, audio.sampleRate, SystemClock.uptimeMillis() - started)
        Log.i(TAG, "synth ${index + 1}/${session.sentences.size} (Hebrew): ${clip.synthMs} ms for ${durationMs(clip)} ms " +
            "(speed ${style.speed}, pitch ${style.pitch})")
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

        var written = 0L  // frames queued since play()
            private set
        private val chunk = sampleRate / 20  // 50 ms, so a newer line stops the audio quickly
        private var lastHead = 0L
        private var wraps = 0L

        /** Frames played since play(); the platform's 32-bit counter extended across wrap-around. */
        fun played(): Long {
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

        /**
         * Queues [samples]; [onStart] runs when playback reaches their first sample, [onProgress]
         * after every written piece (about 50 ms), so callers can act on the playback position while
         * a long piece is being written.
         */
        fun feed(
            samples: FloatArray, isCurrent: () -> Boolean, onStart: (() -> Unit)?, onProgress: (() -> Unit)? = null,
        ): Result {
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
                onProgress?.invoke()
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

        /**
         * Logs how late the sound of [frame] actually comes out compared with the playback-head
         * proxy [proxyMs] (uptime when the head reached it, E0.4): `AudioTrack.getTimestamp` gives
         * the frame being presented and its time; the frame's presentation time is extrapolated.
         */
        fun logPresentation(label: String, frame: Long, proxyMs: Long) {
            val ts = android.media.AudioTimestamp()
            if (!track.getTimestamp(ts)) return
            val presentedNs = ts.nanoTime + (frame - ts.framePosition) * 1_000_000_000L / sampleRate
            // AudioTimestamp.nanoTime is CLOCK_MONOTONIC, the same clock as SystemClock.uptimeMillis().
            val presentedMs = presentedNs / 1_000_000
            Log.i(TAG, "audio-timestamp: sentence $label proxy_t=$proxyMs presented_t=$presentedMs offset_ms=${presentedMs - proxyMs}")
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
                if (!isCurrent()) return stopped()
                val clip = ready.clip
                if (clip == null) EyeControllerHost.instance.showError("voice unavailable or synthesis failed", isCurrent)
                val audioMs = clip?.let(::durationMs) ?: StoryText.spokenMs(sentence)
                val d = ready.decision
                val voice = clip?.let { " | voice ${it.synthMs} ms for $audioMs ms" } ?: " | voice unavailable"
                var startedAtMs = 0L
                val onStart: () -> Unit = {
                    if (startedAtMs == 0L) {  // exactly once per sentence, whatever happens to the audio
                        startedAtMs = SystemClock.uptimeMillis()
                        EyeControllerHost.instance.submitDecision(sentence, d.body, d.error, d.detail + voice, d.source,
                            isCurrent, EyeControllerHost.StoryStep(i, n, startedAtMs, audioMs + GAP_MS), gap = d.gap)
                        Log.i(TAG, "speech: ${i + 1}/$n start t=$startedAtMs audio=$audioMs ms")
                    }
                }
                if (clip != null && audio?.sampleRate != clip.sampleRate) {
                    audio?.close()
                    audio = AudioOut.open(clip.sampleRate)
                    when (audio?.start(isCurrent)) {
                        AudioOut.Result.STALE -> return stopped()
                        AudioOut.Result.FAILED -> {
                            if (isCurrent()) EyeControllerHost.instance.showError("audio output failed", isCurrent)
                            dropAudio()
                        }
                        else -> Unit
                    }
                }
                val out = audio
                if (clip != null && out == null && isCurrent()) EyeControllerHost.instance.showError("audio output unavailable", isCurrent)
                val result = when {
                    // Green only once this sentence's audio really plays (the playback position reached it).
                    clip != null && out != null -> {
                        val firstFrame = out.written  // where this sentence's audio starts in the stream
                        out.feed(clip.samples, isCurrent, {
                            val proxyMs = SystemClock.uptimeMillis()  // the head just reached this sentence
                            EyeControllerHost.instance.setSpeaking(true, isCurrent)
                            onStart()
                            out.logPresentation("${i + 1}/$n", firstFrame, proxyMs)
                        }).let { r ->
                            if (r == AudioOut.Result.OK) out.silence(GAP_MS, isCurrent) else r
                        }
                    }
                    // No voice for this sentence: the eyes keep its timing; an open stream is kept fed.
                    out != null -> {
                        EyeControllerHost.instance.setSpeaking(false)
                        onStart()
                        out.silence(audioMs + GAP_MS, isCurrent)
                    }
                    else -> {
                        EyeControllerHost.instance.setSpeaking(false)
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
                        if (isCurrent()) EyeControllerHost.instance.showError("audio output failed", isCurrent)
                        dropAudio()
                        EyeControllerHost.instance.setSpeaking(false)
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
            EyeControllerHost.instance.setSpeaking(false)
        }
    }

    /**
     * Plays a cloud line as its audio streams in and starts each sentence's eye reaction when the
     * audio reaches it. Before the first audio the stream is kept awake with silence (no audio is
     * inserted afterwards, so positions inside the speech stay exact). No audio (failure, missing
     * key, or no first audio within [FIRST_AUDIO_TIMEOUT_MS]) falls back to the eyes alone.
     */
    private fun playCloud(session: Session, line: CloudLine) {
        val brain = EyeBrainHost.instance
        val isCurrent = { brain.isCurrent(session.generation) }
        val sentences = session.sentences
        val n = sentences.size
        val out = AudioOut.open(GeminiVoice.SAMPLE_RATE)
        val deadline = SystemClock.uptimeMillis() + FIRST_AUDIO_TIMEOUT_MS
        // Until the stream ends, sentence starts are estimated from an average Hebrew speaking rate;
        // the exact proportional starts replace them as soon as the total length is known.
        val estimated = LineTiming.starts(sentences, LineTiming.estimatedFrames(sentences, GeminiVoice.SAMPLE_RATE))
        var exact: LongArray? = null
        var speechStart = -1L  // frame where the speech begins in the stream
        var next = 0
        val fireDue: () -> Unit = {
            while (next < n && speechStart >= 0 && out != null) {
                val starts = exact ?: estimated
                if (out.played() < speechStart + starts[next]) break
                val ready = session.ready[next].getNow(null) ?: break
                val ms = (exact?.let { s -> (if (next + 1 < n) s[next + 1] else line.totalFrames) - s[next] }
                    ?.times(1_000)?.div(GeminiVoice.SAMPLE_RATE)) ?: StoryText.spokenMs(sentences[next])
                EyeControllerHost.instance.setSpeaking(true, isCurrent)
                startEyes(session, next, ready.decision, ms, " | voice gemini", isCurrent)
                next++
            }
        }
        try {
            if (!isCurrent()) return stopped()
            if (out == null) {
                line.call.cancel()
                return playEyesOnly(session, 0, "audio output unavailable", isCurrent)
            }
            when (out.start(isCurrent)) {
                AudioOut.Result.STALE -> return stopped()
                AudioOut.Result.FAILED -> return playEyesOnly(session, 0, "audio output failed", isCurrent)
                AudioOut.Result.OK -> Unit
            }
            var consumed = 0L
            while (true) {
                if (!isCurrent()) return stopped()
                val now = SystemClock.uptimeMillis()
                if (!line.ended && !line.call.cancelled) {
                    val stuck = if (speechStart < 0) now > deadline else now - line.lastAudioAtMs > STALL_TIMEOUT_MS
                    if (stuck) {
                        if (isCurrent()) EyeControllerHost.instance.showError("cloud audio timeout or stall", isCurrent)
                        Log.w(TAG, if (speechStart < 0) "cloud: no audio in time" else "cloud: stream stalled; ending the line")
                        line.call.cancel()  // the producer then ends the line with what it has
                    }
                }
                val chunk = line.pcm.poll(20, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    if (speechStart < 0) speechStart = out.written
                    when (out.feed(chunk, isCurrent, null, fireDue)) {
                        AudioOut.Result.STALE -> return stopped()
                        AudioOut.Result.FAILED -> {
                            line.call.cancel()
                            return playEyesOnly(session, next, "audio output failed", isCurrent)
                        }
                        AudioOut.Result.OK -> consumed += chunk.size
                    }
                } else if (speechStart < 0 && !line.ended) {
                    when (out.silence(SILENCE_STEP_MS, isCurrent)) {
                        AudioOut.Result.STALE -> return stopped()
                        AudioOut.Result.FAILED -> return playEyesOnly(session, next, "audio output failed", isCurrent)
                        AudioOut.Result.OK -> Unit
                    }
                }
                if (line.ended && exact == null) {
                    if (line.totalFrames == 0L) {
                        return playEyesOnly(session, 0, line.error ?: "no audio in time", isCurrent)
                    }
                    exact = LineTiming.starts(sentences, line.totalFrames)
                    Log.i(TAG, "cloud: stream ended, ${line.totalFrames * 1_000 / GeminiVoice.SAMPLE_RATE} ms of speech" +
                        (line.error?.let { " (cut short: $it)" } ?: ""))
                }
                fireDue()
                if (line.ended && consumed >= line.totalFrames && out.played() >= out.written) {
                    if (next < n) Log.i(TAG, "cloud: line over before ${n - next} sentence(s) were decided; skipped")
                    return
                }
                if (line.ended && chunk == null) SystemClock.sleep(10)
            }
        } finally {
            line.call.cancel()
            out?.close()
            EyeControllerHost.instance.setSpeaking(false)
        }
    }

    /** No cloud audio: the eyes follow the remaining sentences alone, on estimated speaking time. */
    private fun playEyesOnly(session: Session, from: Int, reason: String, isCurrent: () -> Boolean) {
        if (!isCurrent()) return stopped()
        EyeControllerHost.instance.showError("cloud voice unavailable or playback failed", isCurrent)
        EyeControllerHost.instance.setSpeaking(false)  // silent from here on: back to the waiting color
        for (i in from until session.sentences.size) {
            val ready = await(session.ready[i], isCurrent) ?: return stopped()
            val ms = StoryText.spokenMs(session.sentences[i])
            startEyes(session, i, ready.decision, ms, " | voice unavailable: $reason", isCurrent)
            if (!waitUntil(SystemClock.uptimeMillis() + ms + GAP_MS, isCurrent)) return stopped()
        }
    }

    private fun startEyes(
        session: Session, i: Int, d: EyeBrainHost.Decision, spokenMs: Long, voice: String, isCurrent: () -> Boolean,
    ) {
        val n = session.sentences.size
        val startMs = SystemClock.uptimeMillis()
        EyeControllerHost.instance.submitDecision(session.sentences[i], d.body, d.error, d.detail + voice, d.source,
            isCurrent, EyeControllerHost.StoryStep(i, n, startMs, spokenMs), gap = d.gap)
        Log.i(TAG, "speech: ${i + 1}/$n start t=$startMs audio=$spokenMs ms")
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
        private const val KEY_FILE = "gemini_api_key"
        private const val WARM_UP_WORD = "שלום"
        const val HEBREW_ANDROID = "android"
        const val HEBREW_GEMINI = "gemini"
        private const val QUEUE_CHUNKS = 1_500  // ≈ 1–3 min of streamed audio; bounds memory
        private const val FIRST_AUDIO_TIMEOUT_MS = 20_000L
        private const val STALL_TIMEOUT_MS = 10_000L  // no new audio for this long ends the line
        private const val THREADS = 2  // fewer threads leave the CPU to the eye model running at the same time (8.3)
        private const val GAP_MS = 250L  // pause between sentences
        private const val POLL_MS = 100L
        private const val WARMUP_MS = 300L  // silence before the first sentence: wakes the speaker path
        private const val SILENCE_STEP_MS = 50L  // silence fed while waiting for the next sentence
        private const val BUFFER_MS = 250L  // audio buffer; bounds how far silence runs ahead

        val instance: SpeechHost by lazy { SpeechHost() }
    }
}
