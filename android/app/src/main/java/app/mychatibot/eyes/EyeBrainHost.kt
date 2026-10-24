package app.mychatibot.eyes

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import app.mychatibot.NativeBridge
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Turns a sentence into an eye reaction as fast as possible:
 * 1. an instant blink so the eyes visibly "hear" it,
 * 2. the owner phrasebook (exact matches) for an immediate answer,
 * 3. otherwise the on-phone eye model (Gemma 3 1B, llama.cpp) on its own thread, never on the UI
 *    or render thread — it classifies the expression; gaze comes from [LookRules].
 * Everything goes to [EyeControllerHost], where the Stage 5 validator and controller decide.
 *
 * Only the newest sentence matters: a new one cancels the running inference, and a result whose
 * generation is no longer current is dropped, never applied. The model is loaded once (model
 * load time is outside the per-sentence timeout) and can be released under memory pressure.
 */
class EyeBrainHost private constructor() {

    private class Request(val generation: Long, val sentence: String, val receivedAtMs: Long)

    private val thread = HandlerThread("eye-brain").apply { start() }
    private val handler = Handler(thread.looper)
    private val submitLock = Any()
    private val generation = AtomicLong(0)
    private val latest = AtomicReference<Request?>(null)
    @Volatile private var phrasebook = Phrasebook.EMPTY
    private var modelsDir: File? = null  // brain thread only
    private var modelName = MODEL_FILE
    private var threads = THREADS
    private var threadsBatch = THREADS
    /** "No reaction" calibration: a non-NONE expression whose logit gap to NONE is below this
     *  (and with no explicit look request) is treated as NONE. 0 = off. */
    @Volatile private var noneGapThreshold = NONE_GAP_THRESHOLD
    private var loadError: String? = null
    private var warmedUp = false  // brain thread only
    private var appContext: Context? = null  // brain thread only
    /** The active prompt bundle's version (E0.7). */
    @Volatile var bundleVersion = BundleStore.DEFAULT_VERSION
        private set
    /** The active prompt bundle itself (for the manifest). */
    @Volatile var activeBundle: PromptBundle? = null
        private set
    @Volatile private var wordsPerSecond = StoryText.DEFAULT_WORDS_PER_SECOND
    @Volatile private var storyContext = false  // off: it lowered story accuracy (6C.5, 2026-10-23)

    /** Loads the phrasebook asset and starts loading the model (app-private files dir) in the background. */
    fun init(context: Context) {
        val app = context.applicationContext
        handler.post {
            val errors = mutableListOf<String>()
            phrasebook = runCatching { Phrasebook.parse(app.assets.open(PHRASEBOOK_ASSET).reader().readText(), errors) }
                .getOrElse { errors += "cannot read $PHRASEBOOK_ASSET: ${it.message}"; Phrasebook.EMPTY }
            errors.forEach { Log.w(TAG, "phrasebook: $it") }
            Log.i(TAG, "phrasebook: ${phrasebook.size} phrases")
            modelsDir = File(app.filesDir, "models")
            appContext = app
            if (startWithFirstWorkingBundle(app)) warmUp()
            ConfigManifest.write(app, activeBundle)
        }
    }

    /**
     * Brain thread, once per process after the model loads, and only if no request has arrived since
     * start (generation 0): the first inferences after a load are slow, so the model judges two look
     * sentences (results discarded), then the eyes glance right and left — a visible "ready". Any
     * real request bumps the generation, which cancels the warm-up inference (it runs as generation
     * 0) and skips the glances; glances never delay a reaction.
     */
    private fun warmUp() {
        if (warmedUp) return
        warmedUp = true
        for (sentence in WARM_UP_SENTENCES) {
            if (generation.get() != 0L) {
                Log.i(TAG, "warm-up skipped: a real request came first")
                return
            }
            val d = classifyWithModel(sentence, 0L, SystemClock.uptimeMillis(), continueStory = false)
            Log.i(TAG, "warm-up: '$sentence' -> ${d.body ?: d.error}")
        }
        if (generation.get() == 0L && Effects.isOn("warmup_glance")) {
            EyeControllerHost.instance.glances(listOf(Look.RIGHT, Look.LEFT)) {
                generation.get() == 0L && Effects.isOn("warmup_glance")
            }
        }
        Log.i(TAG, "warm-up done")
    }

    fun submitSentence(sentence: String) {
        val now = SystemClock.uptimeMillis()
        val hit = phrasebook.lookup(sentence)
        // Allocating the generation and publishing the request happen together, so concurrent
        // callers can never leave an older request in `latest` after a newer one. A phrasebook hit
        // is never published to the model queue (and clears any waiting older request).
        val request = synchronized(submitLock) {
            Request(generation.incrementAndGet(), sentence, now).also { latest.set(if (hit == null) it else null) }
        }
        NativeBridge.nativeBlink()  // instant micro-reaction: the eyes "heard" something
        // Only older requests stop early; a worker already running this request is unaffected.
        NativeBridge.nativeBrainCancelOlderThan(request.generation)

        val isCurrent = { request.generation == generation.get() }
        if (hit != null) {
            val (expression, look) = hit
            val body = """{"expression":"${expression.name}","look":"${look.name.lowercase()}"}"""
            EyeControllerHost.instance.submitDecision(
                sentence, body, null, "${SystemClock.uptimeMillis() - now} ms", IntentSource.PHRASEBOOK, isCurrent,
            )
            return
        }
        handler.post(::processLatest)
    }

    /**
     * Plays a longer text (story, conversation reply) at speaking pace: split into sentences, each
     * reaction shown while its sentence is "spoken", with the mood rules of the controller in between.
     * Replaces any running sentence or story.
     */
    fun submitStory(text: String) {
        val sentences = StoryText.split(text)
        if (sentences.isEmpty()) {
            Log.w(TAG, "story: no sentences")
            return
        }
        val generation = beginRequest()
        val wps = wordsPerSecond
        val context = storyContext
        Log.i(TAG, "story: ${sentences.size} sentences at $wps words/s, context $context")
        handler.post { playStory(generation, sentences, wps, context) }
    }

    /**
     * Starts a new request that supersedes every earlier sentence, story or speech: returns its
     * generation. The eyes blink at once (they "heard" something) and older inferences stop early.
     */
    fun beginRequest(): Long {
        val generation = synchronized(submitLock) {
            this.generation.incrementAndGet().also { latest.set(null) }
        }
        NativeBridge.nativeBlink()
        NativeBridge.nativeBrainCancelOlderThan(generation)
        return generation
    }

    /** Whether [generation] is still the newest request (thread-safe). */
    fun isCurrent(generation: Long) = generation == this.generation.get()

    /**
     * Classifies [sentences] of request [generation] one after another on the brain thread and hands
     * each decision to [onDecision] (called on the brain thread) as soon as it is ready. Stops when
     * the request is superseded. Used by spoken lines, which time the eyes by the real audio.
     */
    fun classifyAhead(generation: Long, sentences: List<String>, onDecision: (Int, Decision) -> Unit) {
        val context = storyContext
        handler.post { classifyEach(generation, sentences, context, onDecision) }
    }

    /**
     * Development only (debug channel): switch to prompt bundle [version] (a file in the app's private
     * `config/bundles/`, or a packaged one). The switch is a new request: running inference is
     * cancelled, speech stops (its generation is stale), the eyes go back to idle and the original
     * shape; then the model's context is rebuilt with the new prompt. An invalid bundle changes
     * nothing (the native side keeps the previous one).
     */
    fun switchBundle(version: Int) {
        beginRequest()
        EyeControllerHost.instance.resetToIdle()
        handler.post {
            val app = appContext ?: return@post
            val bundle = BundleStore.load(app, version)
            if (bundle == null) {
                Log.w(TAG, "bundle: v$version rejected (missing or invalid); keeping v$bundleVersion")
                return@post
            }
            // Persist first (a small atomic pointer write), then switch the model. If the model rejects
            // the bundle, its current context is untouched (built-then-swapped natively) and only the
            // pointer goes back; should that fail too, the next start falls back to last-known-good.
            val previous = activeBundle
            try {
                BundleStore.activate(app, version)
            } catch (e: Exception) {
                Log.w(TAG, "bundle: v$version could not be saved (${e.javaClass.simpleName}); keeping v${previous?.version}")
                return@post
            }
            if (!applyBundle(bundle)) {
                runCatching { BundleStore.restoreActive(app, previous?.version ?: BundleStore.DEFAULT_VERSION) }
                    .onFailure { Log.e(TAG, "bundle: pointer not restored (${it.javaClass.simpleName}); the next start uses last-known-good") }
                return@post
            }
            ConfigManifest.write(app, bundle)
        }
    }

    /**
     * Brain thread, at start: the active bundle, else the last-known-good one, else the packaged
     * default — the first one with which the model actually loads (a bundle can pass the file checks
     * and still fail natively, e.g. a prompt over the token limit). True when the model is loaded.
     */
    private fun startWithFirstWorkingBundle(app: Context): Boolean {
        for (bundle in BundleStore.candidates(app)) {
            if (!applyBundle(bundle)) continue
            if (ensureLoaded()) return true
            if (loadError?.startsWith("model file missing") == true) return false  // no bundle helps
            Log.w(TAG, "bundle: v${bundle.version} failed while loading the model ($loadError); trying the next one")
        }
        return false
    }

    /** Brain thread: hands [bundle] to the native model (rebuilding its context if loaded). */
    private fun applyBundle(bundle: PromptBundle): Boolean {
        val error = NativeBridge.nativeBrainSetPrompt(bundle.prompt.toByteArray(Charsets.UTF_8), bundle.labels.joinToString(","))
        if (error != null) {
            Log.w(TAG, "bundle: v${bundle.version} not applied: $error; keeping v$bundleVersion")
            return false
        }
        activeBundle = bundle
        bundleVersion = bundle.version
        Log.i(TAG, "bundle: v${bundle.version} active, ${bundle.labels.size} labels")
        return true
    }

    /** Development only (debug channel): whether stories give the model the earlier sentences. */
    fun setStoryContext(enabled: Boolean) {
        storyContext = enabled
    }

    /** Development only (debug channel): speaking pace used to time story sentences. */
    fun setWordsPerSecond(value: Float) {
        if (value in 0.5f..6f) wordsPerSecond = value
    }

    /** Frees the model (e.g. on memory pressure); the next sentence reloads it. */
    fun release() {
        val stale = synchronized(submitLock) { generation.incrementAndGet() }
        NativeBridge.nativeBrainCancelOlderThan(stale)  // anything in flight is now stale
        handler.post {
            NativeBridge.nativeBrainRelease()
            Log.i(TAG, "model released")
        }
    }

    /**
     * Development/benchmark only (debug channel): switch the model file inside the app's private
     * models dir and/or the thread counts. A model switch releases and reloads; thread changes apply
     * to the loaded model directly. Null keeps the current value.
     */
    fun configure(model: String?, threads: Int?, threadsBatch: Int?, noneGap: Float? = null) = handler.post {
        noneGap?.takeIf { it in 0f..20f }?.let { noneGapThreshold = it }
        if (model != null && model != modelName) {
            if (!Regex("""[A-Za-z0-9._-]+\.gguf""").matches(model)) {
                Log.w(TAG, "configure: rejected model name '$model'")
                return@post
            }
            NativeBridge.nativeBrainRelease()
            modelName = model
        }
        threads?.takeIf { it in 1..8 }?.let { this.threads = it }
        threadsBatch?.takeIf { it in 1..8 }?.let { this.threadsBatch = it }
        if (ensureLoaded()) NativeBridge.nativeBrainSetThreads(this.threads, this.threadsBatch)
        Log.i(TAG, "configure: model=$modelName threads=${this.threads} batch=${this.threadsBatch} " +
            "none_gap=$noneGapThreshold loaded=${loadError == null}")
    }

    private fun ensureLoaded(): Boolean {
        val file = modelsDir?.let { File(it, modelName) } ?: return false
        if (!file.isFile) {
            loadError = "model file missing: ${file.path}"
            return false
        }
        val error = NativeBridge.nativeBrainLoad(file.path, threads)
        loadError = error
        if (error != null) Log.w(TAG, "model load failed: $error")
        else NativeBridge.nativeBrainSetThreads(threads, threadsBatch)
        return error == null
    }

    private fun processLatest() {
        val request = latest.getAndSet(null) ?: return
        // Checked again on the controller thread right before anything is applied.
        val isCurrent = { request.generation == generation.get() }
        val d = classifyWithModel(request.sentence, request.generation, request.receivedAtMs, continueStory = false)
        EyeControllerHost.instance.submitDecision(request.sentence, d.body, d.error, d.detail, d.source, isCurrent)
    }

    /** What the eyes should do for one sentence: a `{"expression","look"}` body, or an error. */
    class Decision(val body: String?, val error: String?, val detail: String, val source: IntentSource)

    private fun classifyWithModel(sentence: String, generation: Long, receivedAtMs: Long, continueStory: Boolean): Decision {
        if (!ensureLoaded()) return Decision(null, "MODEL UNAVAILABLE ${loadError ?: ""}", "", IntentSource.MODEL)
        val fields = NativeBridge.nativeBrainInfer(sentence, TIMEOUT_MS, generation, continueStory).split('\t')
        val ok = fields.getOrNull(0) == "OK"
        val payload = fields.getOrNull(1).orEmpty()
        val endToEnd = SystemClock.uptimeMillis() - receivedAtMs
        val gap = fields.getOrNull(7)?.toFloatOrNull()
        val detail = if (fields.size >= 7) {
            "$endToEnd ms (model ${fields[6]} ms: prompt ${fields[2]} tok ${fields[4]} ms, ${fields[3]} tok ${fields[5]} ms, " +
                "gap ${"%.2f".format(gap ?: -1f)})"
        } else "$endToEnd ms"
        Log.i(TAG, "brain: '$sentence' -> ${fields.joinToString(" | ")} | end-to-end $endToEnd ms")
        if (!ok) return Decision(null, "MODEL ERROR $payload", detail, IntentSource.MODEL)
        val body = composeBody(payload, sentence, gap)
            ?: return Decision(null, "MODEL OUTPUT REJECTED not {\"expression\":…}: $payload", detail, IntentSource.MODEL)
        return Decision(body, null, detail, IntentSource.MODEL)
    }

    /**
     * Story playback on the brain thread: each sentence is classified (phrasebook or model) as soon
     * as the previous one is done, i.e. ahead of time while earlier sentences are still showing, and
     * scheduled at its own start. The first sentence starts when its decision is ready; each next one
     * when the previous has been spoken, or later if its decision was late. A newer sentence or story
     * stops this one (its scheduled decisions become stale and are dropped).
     *
     * With [withContext] the model sees the story so far (kept in its cache, so only the new sentence
     * is processed) and every sentence goes to the model, so the context has no gaps; without it each
     * sentence is judged alone and the phrasebook answers exact matches.
     */
    private fun playStory(generation: Long, sentences: List<String>, wordsPerSecond: Float, withContext: Boolean) {
        val isCurrent = { isCurrent(generation) }
        var nextStartMs = 0L
        classifyEach(generation, sentences, withContext) { i, d ->
            val sentence = sentences[i]
            val startMs = maxOf(nextStartMs, SystemClock.uptimeMillis())
            val spokenMs = StoryText.spokenMs(sentence, wordsPerSecond)
            nextStartMs = startMs + spokenMs
            EyeControllerHost.instance.submitDecision(sentence, d.body, d.error, d.detail, d.source, isCurrent,
                EyeControllerHost.StoryStep(i, sentences.size, startMs, spokenMs))
        }
    }

    /** Brain thread: decides each sentence in order (see [playStory] for [withContext]); stops when superseded. */
    private fun classifyEach(
        generation: Long, sentences: List<String>, withContext: Boolean, onDecision: (Int, Decision) -> Unit,
    ) {
        for ((i, sentence) in sentences.withIndex()) {
            if (!isCurrent(generation)) {
                Log.i(TAG, "request superseded at sentence ${i + 1}/${sentences.size}")
                return
            }
            val began = SystemClock.uptimeMillis()
            val d = if (withContext) {
                classifyWithModel(sentence, generation, began, continueStory = i > 0)
            } else phrasebook.lookup(sentence)?.let { (expression, look) ->
                Decision("""{"expression":"${expression.name}","look":"${look.name.lowercase()}"}""", null,
                    "${SystemClock.uptimeMillis() - began} ms", IntentSource.PHRASEBOOK)
            } ?: classifyWithModel(sentence, generation, began, continueStory = false)
            onDecision(i, d)
        }
    }

    /**
     * The model returns `{"expression":"X"}`; the gaze comes from [LookRules]. An explicit look
     * request the model judged "no reaction" still turns the eyes (NEUTRAL + that direction).
     */
    private fun composeBody(modelJson: String, sentence: String, noneGap: Float?): String? {
        // The model output must be exactly {"expression":"<string>"}; anything else (extra fields,
        // nesting, trailing data) is an explicit rejection — the model never supplies the look.
        val fields = runCatching { StrictJson.parseFlatObject(modelJson) }.getOrNull()
        val expression = (fields?.takeIf { it.keys == setOf("expression") }?.get("expression") as? String)
            ?: return null
        val look = LookRules.detect(sentence)
        val threshold = noneGapThreshold
        val chosen = when {
            expression == Expression.NONE.name && look != Look.CENTER -> Expression.NEUTRAL.name
            // Calibration: a weak reaction that was nearly "no reaction", with no look request.
            threshold > 0f && look == Look.CENTER && noneGap != null && noneGap < threshold &&
                expression != Expression.NONE.name && expression != Expression.NEUTRAL.name -> Expression.NONE.name
            else -> expression
        }
        return """{"expression":"$chosen","look":"${look.name.lowercase()}"}"""
    }

    companion object {
        private const val TAG = "ChatiBrain"
        const val MODEL_FILE = "gemma-3-1b-it-Q4_K_M.gguf"
        private const val PHRASEBOOK_ASSET = "phrasebook.txt"
        private const val THREADS = 6
        private const val TIMEOUT_MS = 8_000
        private const val NONE_GAP_THRESHOLD = 0f  // off until calibrated
        /** Start-up warm-up: the eyes look right, then left (a newer one waits for the controller's dwell). */
        private val WARM_UP_SENTENCES = listOf("Look to the right.", "Look to the left.")

        val instance: EyeBrainHost by lazy { EyeBrainHost() }
    }
}
