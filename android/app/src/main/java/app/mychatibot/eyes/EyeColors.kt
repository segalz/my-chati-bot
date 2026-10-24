package app.mychatibot.eyes

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Collections

/** Immutable owner configuration; [text] retains the exact source for the manifest. */
data class ColorTable private constructor(
    val waiting: Int,
    val speaking: Int,
    val tint: Map<Expression, Int>,
    val strong: Map<Expression, Int>,
    val tintMin: Float,
    val tintMax: Float,
    val fadeMs: Int,
    val gapLow: Float,
    val gapHigh: Float,
    val text: String,
) {
    fun strength(gap: Float?): Float = when {
        gap == null || gap.isNaN() -> 1f
        else -> ((gap - gapLow) / (gapHigh - gapLow)).coerceIn(0f, 1f)
    }

    fun colorFor(speaking: Boolean, expression: Expression?, strength: Float): Int {
        strong[expression]?.let { return it }
        val base = if (speaking) this.speaking else waiting
        val color = tint[expression] ?: return base
        val bounded = if (strength.isNaN()) 1f else strength.coerceIn(0f, 1f)
        return EyeColors.blend(base, color, tintMin + (tintMax - tintMin) * bounded)
    }

    companion object {
        private const val MAX_FILE_CHARS = 64 * 1024
        private val SCALARS = setOf("waiting", "speaking", "tint_min", "tint_max", "fade_ms", "gap_low", "gap_high")
        private val COLOR = Regex("#[0-9a-fA-F]{6}")

        val DEFAULT: ColorTable = parse("""
            # Eye colors (#15). waiting/speaking: base color; tint X: an emotion's color mixed in by strength;
            # strong X: an emotion shown in its own color; tint_min/tint_max: mix at the lowest/highest strength;
            # gap_low/gap_high: eye-model confidence (logit gap) mapped to strength 0..1 (#13, chosen on dev sentences)
            waiting=#00BFFF
            speaking=#00E676
            tint HAPPY=#FFD54F
            tint SAD=#3D5AFE
            tint SURPRISED=#FFFFFF
            tint SLEEPY=#9575CD
            tint BORED=#9E9E9E
            tint WINK_LEFT=#FFD54F
            tint WINK_RIGHT=#FFD54F
            strong ANGRY=#FF2A2A
            tint_min=0.15
            tint_max=0.55
            fade_ms=300
            gap_low=10.3
            gap_high=20.1
        """.trimIndent() + "\n", mutableListOf())!!

        /** All scalars are required; omitted emotion entries leave that expression untinted. */
        fun parse(text: String, errors: MutableList<String>): ColorTable? {
            val initialErrors = errors.size
            if (text.length > MAX_FILE_CHARS) {
                errors += "colors longer than $MAX_FILE_CHARS chars"
                return null
            }
            val scalars = mutableMapOf<String, String>()
            val tint = mutableMapOf<Expression, Int>()
            val strong = mutableMapOf<Expression, Int>()
            val seen = mutableSetOf<String>()
            for ((n, raw) in text.lines().withIndex()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith('#')) continue
                val parts = line.split('=')
                if (parts.size != 2) {
                    errors += "line ${n + 1}: expected key=value"
                    continue
                }
                val key = parts[0].trim()
                val value = parts[1].trim()
                val emotion = key.split(Regex("\\s+"))
                val canonical = emotion.joinToString(" ")
                if (!seen.add(canonical)) {
                    errors += "line ${n + 1}: repeated $canonical"
                    continue
                }
                if (key in SCALARS) {
                    scalars[key] = value
                } else if (emotion.size == 2 && emotion[0] in setOf("tint", "strong")) {
                    val expression = Expression.entries.firstOrNull { it.name == emotion[1] && it != Expression.NONE }
                    val color = value.takeIf(COLOR::matches)?.substring(1)?.toIntOrNull(16)
                    if (expression == null) errors += "line ${n + 1}: unknown expression ${emotion[1]}"
                    if (color == null) errors += "line ${n + 1}: color must be #RRGGBB"
                    if (expression != null && color != null) {
                        (if (emotion[0] == "tint") tint else strong)[expression] = color
                    }
                } else errors += "line ${n + 1}: unknown key $key"
            }
            for (key in SCALARS - scalars.keys) errors += "missing $key"
            fun color(key: String): Int? = scalars[key]?.takeIf(COLOR::matches)?.substring(1)?.toIntOrNull(16)
                .also { if (it == null) errors += "$key must be #RRGGBB" }
            fun number(key: String): Float? = scalars[key]?.toFloatOrNull()?.takeIf { it.isFinite() }
                .also { if (it == null) errors += "$key must be finite" }
            val waiting = color("waiting")
            val speaking = color("speaking")
            val min = number("tint_min")
            val max = number("tint_max")
            val low = number("gap_low")
            val high = number("gap_high")
            val fade = scalars["fade_ms"]?.toIntOrNull()?.takeIf { it in 0..5000 }
            if (fade == null) errors += "fade_ms must be an integer in 0..5000"
            if (min != null && min !in 0f..1f) errors += "tint_min must be 0..1"
            if (max != null && max !in 0f..1f) errors += "tint_max must be 0..1"
            if (min != null && max != null && min > max) errors += "tint_min must be <= tint_max"
            if (low != null && high != null && (low >= high || !(high - low).isFinite())) {
                errors += "gap_low must be < gap_high with a finite range"
            }
            if (errors.size != initialErrors) return null
            return ColorTable(waiting!!, speaking!!, Collections.unmodifiableMap(tint), Collections.unmodifiableMap(strong),
                min!!, max!!, fade!!, low!!, high!!, text)
        }
    }
}

/** Publishes a complete valid table to readers on any thread. */
object EyeColors {
    private const val TAG = "ChatiEyes"
    private const val FILE = "colors.txt"
    @Volatile var table: ColorTable = ColorTable.DEFAULT

    @Synchronized fun init(context: Context) {
        val privateFile = File(context.filesDir, "config/$FILE")
        table = (if (privateFile.exists()) read("private $FILE") { privateFile.readText() } else null)
            ?: read("asset $FILE") { context.assets.open(FILE).bufferedReader(Charsets.UTF_8).use { it.readText() } }
            ?: ColorTable.DEFAULT
    }

    @Synchronized fun reload(context: Context): Boolean {
        val next = read("private $FILE") { File(context.filesDir, "config/$FILE").readText() } ?: return false
        table = next
        return true
    }

    private fun read(source: String, text: () -> String): ColorTable? {
        val errors = mutableListOf<String>()
        val parsed = runCatching { ColorTable.parse(text(), errors) }.getOrElse {
            errors += "cannot read: ${it.message}"
            null
        }
        errors.forEach { Log.w(TAG, "colors ($source): $it") }
        return parsed
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xff
            val y = (b shr shift) and 0xff
            return Math.round(x + (y - x) * t).coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
