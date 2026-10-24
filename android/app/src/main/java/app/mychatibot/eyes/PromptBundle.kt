package app.mychatibot.eyes

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A prompt bundle for the eye model (E0.7): a version, the labels it may choose and the system
 * prompt. Bundles are files, so a new emotion can be tried by pushing a file instead of installing
 * an APK. Every label must be an [Expression] the app knows (so it has a color, a voice style and a
 * drawing); NONE and NEUTRAL are required.
 *
 * File format (`bundles/v<N>.txt`):
 * ```
 * # comments
 * version=5
 * labels=NEUTRAL,HAPPY,…,NONE
 * ---
 * <system prompt, verbatim to the end of the file>
 * ```
 */
data class PromptBundle(val version: Int, val labels: List<String>, val prompt: String) {

    companion object {
        const val MAX_FILE_CHARS = 64 * 1024
        /** Same limit as the native side (UTF-8 bytes); the token limit (450) is checked natively. */
        const val MAX_PROMPT_BYTES = 16_000
        const val MAX_LABELS = 32

        /** Null (with [errors]) unless the whole file is valid. Line endings are normalized. */
        fun parse(text: String, errors: MutableList<String>): PromptBundle? {
            if (text.length > MAX_FILE_CHARS) {
                errors += "bundle longer than $MAX_FILE_CHARS chars"
                return null
            }
            val normalized = text.replace("\r\n", "\n")
            val split = normalized.indexOf("\n---\n")
            if (split < 0) {
                errors += "missing the '---' line before the prompt"
                return null
            }
            var version: Int? = null
            var labels: List<String>? = null
            for (raw in normalized.substring(0, split).lines()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=', "").trim()
                when (key) {
                    "version" -> version = value.toIntOrNull()?.takeIf { it in 1..9_999 }
                        ?: run { errors += "version must be 1..9999"; return null }
                    "labels" -> labels = value.split(',').map { it.trim() }
                        .also { if (it.any(String::isEmpty)) { errors += "empty label"; return null } }
                    else -> {
                        errors += "unknown header '$key'"
                        return null
                    }
                }
            }
            val prompt = normalized.substring(split + 5).removeSuffix("\n")
            if (version == null) errors += "missing version"
            val known = Expression.entries.map { it.name }.toSet()
            when {
                labels == null -> errors += "missing labels"
                labels.size > MAX_LABELS -> errors += "more than $MAX_LABELS labels"
                labels.toSet().size != labels.size -> errors += "repeated label"
                labels.any { it !in known } -> errors += "unknown label(s): ${labels.filter { it !in known }}"
                "NONE" !in labels || "NEUTRAL" !in labels -> errors += "labels must include NONE and NEUTRAL"
            }
            if (prompt.isBlank() || prompt.toByteArray(Charsets.UTF_8).size > MAX_PROMPT_BYTES) {
                errors += "prompt must be 1..$MAX_PROMPT_BYTES UTF-8 bytes"
            }
            return if (errors.isEmpty()) PromptBundle(version!!, labels!!, prompt) else null
        }
    }
}

/**
 * Where bundles live and which one is active (E0.7): `files/config/bundles/v<N>.txt` (pushed over
 * the debug channel), the active version in `files/config/bundles/active`, the previous active one
 * in `active.lkg`. The packaged `assets/bundles/v4.txt` is the default. Activation writes the
 * pointer atomically; a missing or invalid active bundle falls back to the last-known-good one, then
 * to the packaged default.
 */
object BundleStore {
    private const val TAG = "ChatiBrain"
    const val DEFAULT_VERSION = 4

    /**
     * The bundles to try at start, best first: the active one, the last-known-good one, the packaged
     * default (read from the APK itself, so a broken pushed file can never shadow it). Invalid ones
     * are left out; the caller moves on when a bundle fails while the model loads.
     */
    fun candidates(context: Context): List<PromptBundle> {
        val dir = dir(context)
        val out = mutableListOf<PromptBundle>()
        for (pointer in listOf("active", "active.lkg")) {
            val version = runCatching { readBounded(File(dir, pointer), 16)?.trim()?.toInt() }.getOrNull() ?: continue
            val bundle = load(context, version)
            if (bundle == null) Log.w(TAG, "bundle: $pointer → v$version is missing or invalid; trying the next fallback")
            else if (out.none { it.version == bundle.version }) out += bundle
        }
        packaged(context)?.let { p -> if (out.none { it == p }) out += p }
        return out
    }

    /** Reads and validates bundle [version] (private file first, then the packaged asset). */
    fun load(context: Context, version: Int): PromptBundle? {
        val text = fileText(context, version) ?: return null
        val errors = mutableListOf<String>()
        val bundle = PromptBundle.parse(text, errors)
        errors.forEach { Log.w(TAG, "bundle v$version: $it") }
        return bundle?.takeIf { it.version == version }
    }

    /** Makes [version] active (atomic pointer swap); the previous active version becomes last-known-good. */
    fun activate(context: Context, version: Int) {
        val dir = dir(context).apply { mkdirs() }
        val active = File(dir, "active")
        if (active.isFile) replace(dir, "active.lkg", active.readText())
        replace(dir, "active", "$version\n")
    }

    /** Puts [version] back as active after a rejected switch, leaving last-known-good untouched. */
    fun restoreActive(context: Context, version: Int) {
        replace(dir(context).apply { mkdirs() }, "active", "$version\n")
    }

    /**
     * The bundle file's text, read with a size bound: private file first, then the packaged asset —
     * except the packaged default version, which always comes from the APK (never shadowed).
     */
    fun fileText(context: Context, version: Int): String? {
        if (version == DEFAULT_VERSION) return assetText(context, version)
        val file = File(dir(context), "v$version.txt")
        return if (file.isFile) readBounded(file, PromptBundle.MAX_FILE_CHARS) else assetText(context, version)
    }

    private fun assetText(context: Context, version: Int): String? = runCatching {
        context.assets.open("bundles/v$version.txt").reader().use { r ->
            val buf = CharArray(PromptBundle.MAX_FILE_CHARS + 1)
            var n = 0
            while (n < buf.size) {
                val k = r.read(buf, n, buf.size - n)
                if (k < 0) break
                n += k
            }
            if (n > PromptBundle.MAX_FILE_CHARS) null else String(buf, 0, n)
        }
    }.getOrNull()

    /** Null when missing, unreadable, or larger than [maxBytes] (checked before reading). */
    private fun readBounded(file: File, maxBytes: Int): String? =
        if (!file.isFile || file.length() > maxBytes) null else runCatching { file.readText() }.getOrNull()

    /** The packaged default, always read from the APK (never shadowed by a private file). */
    private fun packaged(context: Context): PromptBundle? {
        val text = assetText(context, DEFAULT_VERSION) ?: return null
        val errors = mutableListOf<String>()
        return PromptBundle.parse(text, errors).also { errors.forEach { e -> Log.e(TAG, "packaged bundle: $e") } }
    }

    private fun dir(context: Context) = File(context.filesDir, "config/bundles")

    private fun replace(dir: File, name: String, text: String) {
        val temporary = File(dir, "$name.tmp")
        try {
            FileOutputStream(temporary).use {
                it.write(text.toByteArray())
                it.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), File(dir, name).toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
