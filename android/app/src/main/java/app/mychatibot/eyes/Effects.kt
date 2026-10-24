package app.mychatibot.eyes

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime expressive switches, persisted in the app's private config/effects.txt.
 * colors=off keeps the original blue eyes (no speaking green or emotion tint),
 * matching the behaviour before the color feature.
 */
object Effects {
    private const val TAG = "ChatiEyes"
    private const val FILE = "effects.txt"

    val KNOWN: Map<String, Boolean> = Collections.unmodifiableMap(mapOf(
        "colors" to true,
        "intensity" to true,
        "idle_glance" to true,
        "warmup_glance" to true,
        "thinking" to true,
        "sleep" to true,
        "error_eyes" to true,
        "fx_sweat" to false,
        "fx_anger" to false,
        "fx_sparkles" to false,
        "fx_qmark" to false,
        "fx_notes" to false,
        "fx_rain" to false,

    ))

    @Volatile private var state: Map<String, Boolean> = KNOWN
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun isOn(name: String): Boolean {
        state[name]?.let { return it }
        if (warned.add(name)) Log.w(TAG, "effects: unknown switch $name")
        return false
    }

    /**
     * One switch per line: `colors=on`. Blank lines and `#` comments are ignored; malformed,
     * unknown, bad-value or repeated lines are reported in [errors] and skipped.
     * Switches not listed keep their [KNOWN] default.
     */
    fun parse(text: String, errors: MutableList<String>): Map<String, Boolean> =
        parse(text, errors, KNOWN)

    private fun parse(text: String, errors: MutableList<String>, defaults: Map<String, Boolean>): Map<String, Boolean> {
        val result = defaults.toMutableMap()
        val seen = mutableSetOf<String>()
        for ((n, raw) in text.lines().withIndex()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split('=')
            if (parts.size != 2) {
                errors += "line ${n + 1}: expected 'name=on|off'"
                continue
            }
            val name = parts[0].trim()
            val value = parts[1].trim()
            when {
                name !in KNOWN -> errors += "line ${n + 1}: unknown switch $name"
                value != "on" && value != "off" -> errors += "line ${n + 1}: $name must be on|off"
                !seen.add(name) -> errors += "line ${n + 1}: $name repeated"
                else -> result[name] = value == "on"
            }
        }
        return Collections.unmodifiableMap(result)
    }

    /** Pure, all-or-nothing merge; omitted switches retain their current value. */
    fun merge(current: Map<String, Boolean>, changes: String, errors: MutableList<String>): Map<String, Boolean>? {
        val merged = parse(changes.replace(',', '\n'), errors, KNOWN.mapValues { (name, default) -> current[name] ?: default })
        return merged.takeIf { errors.isEmpty() }
    }

    /** Loads valid private configuration, last-known-good copy, asset, then defaults. */
    internal fun load(
        dir: File,
        assetText: String?,
        warn: (String) -> Unit = {},
    ): Map<String, Boolean> {
        fun valid(text: String): Map<String, Boolean>? {
            val errors = mutableListOf<String>()
            return parse(text, errors).takeIf { errors.isEmpty() }
        }
        val file = File(dir, FILE)
        if (file.exists()) {
            runCatching { valid(file.readText()) }.getOrNull()?.let { return it }
            warn("effects: invalid or unreadable $FILE; trying last-known-good copy, then asset/defaults")
            runCatching { valid(File(dir, "$FILE.lkg").readText()) }.getOrNull()?.let { return it }
        }
        return assetText?.let { valid(it) } ?: KNOWN
    }

    /** Syncs temporary files before atomic replacement; preserves the previous valid file. */
    internal fun save(dir: File, state: Map<String, Boolean>) {
        val file = File(dir, FILE)
        val temporary = File(dir, "$FILE.tmp")
        val backup = File(dir, "$FILE.lkg")
        val backupTemporary = File(dir, "$FILE.lkg.tmp")
        fun writeSynced(target: File, bytes: ByteArray) {
            FileOutputStream(target).use {
                it.write(bytes)
                it.fd.sync()
            }
        }
        try {
            check(dir.isDirectory || dir.mkdirs()) { "cannot create $dir" }
            val text = state.entries.joinToString("\n", postfix = "\n") { (name, on) ->
                "$name=${if (on) "on" else "off"}"
            }
            writeSynced(temporary, text.toByteArray(Charsets.UTF_8))
            if (file.isFile) {
                val current = file.readBytes()
                val errors = mutableListOf<String>()
                parse(current.toString(Charsets.UTF_8), errors)
                if (errors.isEmpty()) {
                    writeSynced(backupTemporary, current)
                    replaceAtomically(backupTemporary, backup)
                }
            }
            replaceAtomically(temporary, file)
        } finally {
            temporary.delete()
            backupTemporary.delete()
        }
    }

    @Synchronized fun init(context: Context) {
        val assetText = runCatching {
            context.assets.open(FILE).bufferedReader().use { it.readText() }
        }.getOrNull()
        state = load(File(context.filesDir, "config"), assetText) { Log.w(TAG, it) }
        Log.i(TAG, "effects: $state")
    }

    /** Persists a validated full snapshot before publishing it to readers on any thread. */
    @Synchronized fun apply(context: Context, changes: String): Boolean {
        val errors = mutableListOf<String>()
        val next = merge(state, changes, errors)
        if (next == null) {
            errors.forEach { Log.w(TAG, "effects: $it") }
            return false
        }
        return runCatching {
            save(File(context.filesDir, "config"), next)
            state = next
            Log.i(TAG, "effects: $state")
            true
        }.getOrElse { Log.w(TAG, "effects: cannot apply: ${it.message}"); false }
    }

    /**
     * Atomic rename that also replaces an existing target (File.renameTo does not on every platform);
     * a file system without atomic moves gets a plain replacing move.
     */
    private fun replaceAtomically(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
