package app.mychatibot.eyes

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * The tested state, written whenever configuration changes (E0, testing.md › Rollback): app version
 * and APK hash, the active prompt bundle (version and a hash of its content as applied), and every
 * runtime switch. Kept as `files/config/manifest.txt`; the regression tooling copies it next to each
 * measurement so a state can be identified and restored as a whole.
 *
 * The manifest is a record, not part of activation: a bundle switch is complete when the native
 * side and the atomic `active` pointer agree. A failed manifest write is logged as an error (and
 * shows up as a missing/stale manifest in the regression run); it does not undo a valid switch.
 */
object ConfigManifest {
    private const val TAG = "ChatiEyes"
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "config-manifest") }
    @Volatile private var apkHash: Pair<String, String>? = null  // apk path to its SHA-256

    /** Captures the state now (any thread) and writes the file on a background thread. */
    /** [bundle] is the bundle actually applied (null before any). */
    fun write(context: Context, bundle: PromptBundle?) {
        val app = context.applicationContext
        val effects = Effects.KNOWN.keys.sorted().associateWith { Effects.isOn(it) }
        writer.execute { writeNow(app, bundle, effects) }
    }

    private fun writeNow(context: Context, bundle: PromptBundle?, effects: Map<String, Boolean>) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val apk = File(context.applicationInfo.sourceDir)
            // Hash of the content as applied (version, labels, prompt), whatever file it came from.
            val content = bundle?.let { "${it.version}\n${it.labels.joinToString(",")}\n${it.prompt}" }.orEmpty()
            val text = buildString {
                appendLine("app=${info.versionName} (${info.longVersionCode})")
                appendLine("apk_sha256=${apkSha256(apk)}")
                appendLine("bundle=v${bundle?.version ?: 0} sha256=${sha256(content.toByteArray(Charsets.UTF_8))}")
                effects.forEach { (name, on) -> appendLine("effect $name=${if (on) "on" else "off"}") }
            }
            val dir = File(context.filesDir, "config").apply { mkdirs() }
            File(dir, "manifest.txt").writeText(text)
            Log.i(TAG, "manifest: ${text.replace('\n', ' ').trim()}")
        }.onFailure { Log.e(TAG, "manifest not written: ${it.javaClass.simpleName}") }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** The APK does not change while the app runs: hash it once per path. */
    private fun apkSha256(apk: File): String {
        apkHash?.let { (path, hash) -> if (path == apk.path) return hash }
        return sha256(apk).also { apkHash = apk.path to it }
    }

    /** Streams the file (the APK is tens of MB). */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
