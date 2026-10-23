package app.mychatibot.eyes

/**
 * Expressions the eyes can show. The first nine are espp::ExpressiveEyes::Expression in upstream
 * declaration order (the ordinal is what the native engine receives); NONE means "do not react".
 */
enum class Expression {
    NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT, NONE;

    /** Ordinal for the native engine, or null for NONE. */
    val engineId: Int? get() = if (this == NONE) null else ordinal
}

/** Gaze direction as a word (what the eye model emits); LEFT/RIGHT are as seen by the viewer. */
enum class Look(val x: Float, val y: Float) {
    CENTER(0f, 0f), LEFT(-1f, 0f), RIGHT(1f, 0f), UP(0f, -1f), DOWN(0f, 1f);
}

/** Who asked: the eye model, the owner phrasebook, or the developer debug channel. */
enum class IntentSource { MODEL, PHRASEBOOK, DEBUG }

/**
 * A validated request for the eyes. Bounded by construction: the model can only pick an
 * expression and a gaze word; how long and how strongly is decided by [EyeController].
 */
data class EyeIntent(
    val expression: Expression,
    val look: Look,
    val source: IntentSource,
    /** Optional override of the controller's hold time. */
    val holdMs: Long? = null,
    val sessionId: String? = null,
    /** Monotonic deadline (ms) after which the intent must not be applied; null = no deadline. */
    val expiresAtMs: Long? = null,
)

sealed interface ParseResult {
    data class Valid(val intent: EyeIntent) : ParseResult
    data class Rejected(val reason: String) : ParseResult
}

/**
 * Strict parser for the wire format (schema_version 1):
 * ```
 * {"schema_version":1, "expression":"HAPPY", "look":"left",
 *  "hold_ms":2000, "session_id":"abc", "expires_in_ms":5000}
 * ```
 * `expression` and `look` are required; the rest optional. Unknown or duplicate keys, nested
 * values, trailing content, wrong types, non-integer numbers and out-of-range values are rejected
 * — nothing is clamped silently. `expression` is upper case and `look` lower case, exactly as the
 * eye model's grammar produces them. Timing belongs to the controller: a MODEL intent may not
 * carry `hold_ms` (only DEBUG may).
 */
object EyeIntentParser {
    const val SCHEMA_VERSION = 1
    const val MAX_PAYLOAD_CHARS = 1024
    const val MIN_HOLD_MS = 300L
    const val MAX_HOLD_MS = 10_000L
    const val MAX_EXPIRES_IN_MS = 60_000L
    private const val MAX_SESSION_ID_CHARS = 64
    private val SESSION_ID = Regex("[A-Za-z0-9_-]+")
    private val KNOWN_KEYS = setOf("schema_version", "expression", "look", "hold_ms", "session_id", "expires_in_ms")

    fun parse(json: String, source: IntentSource, nowMs: Long): ParseResult {
        if (json.length > MAX_PAYLOAD_CHARS) return reject("payload longer than $MAX_PAYLOAD_CHARS chars")
        val obj = try {
            StrictJson.parseFlatObject(json)
        } catch (e: StrictJson.SyntaxError) {
            return reject("malformed JSON: ${e.message}")
        }
        val unknown = obj.keys.filterNot { it in KNOWN_KEYS }
        if (unknown.isNotEmpty()) return reject("unknown field(s): ${unknown.joinToString()}")

        val version = obj["schema_version"] as? Long ?: return reject("schema_version must be an integer")
        if (version != SCHEMA_VERSION.toLong()) return reject("unsupported schema_version $version")

        val exprName = obj["expression"] as? String ?: return reject("expression must be a string")
        val expression = Expression.entries.firstOrNull { it.name == exprName }
            ?: return reject("unknown expression '$exprName'")

        val lookName = obj["look"] as? String ?: return reject("look must be a string")
        val look = Look.entries.firstOrNull { it.name.lowercase() == lookName }
            ?: return reject("unknown look '$lookName'")

        var holdMs: Long? = null
        if ("hold_ms" in obj) {
            if (source != IntentSource.DEBUG) return reject("hold_ms is controller-owned; only the debug channel may set it")
            holdMs = obj["hold_ms"] as? Long ?: return reject("hold_ms must be an integer")
            if (holdMs !in MIN_HOLD_MS..MAX_HOLD_MS) return reject("hold_ms out of range $MIN_HOLD_MS..$MAX_HOLD_MS")
        }

        var sessionId: String? = null
        if ("session_id" in obj) {
            sessionId = obj["session_id"] as? String ?: return reject("session_id must be a string")
            if (sessionId.length > MAX_SESSION_ID_CHARS || !SESSION_ID.matches(sessionId)) {
                return reject("invalid session_id")
            }
        }

        var expiresAtMs: Long? = null
        if ("expires_in_ms" in obj) {
            val expiresIn = obj["expires_in_ms"] as? Long ?: return reject("expires_in_ms must be an integer")
            if (expiresIn !in 1..MAX_EXPIRES_IN_MS) return reject("expires_in_ms out of range 1..$MAX_EXPIRES_IN_MS")
            if (nowMs > Long.MAX_VALUE - expiresIn) return reject("deadline not representable")
            expiresAtMs = nowMs + expiresIn
        }

        return ParseResult.Valid(EyeIntent(expression, look, source, holdMs, sessionId, expiresAtMs))
    }

    /**
     * The eye model emits the bare v4 body `{"expression":"…","look":"…"}` (what it was benchmarked
     * on); the app, not the model, adds the schema envelope right after the opening brace. Nothing
     * is trimmed or repaired: oversized input or anything not starting exactly with `{` is passed
     * through unchanged so [parse] rejects it.
     */
    fun wrapModelOutput(body: String): String {
        if (body.length > MAX_PAYLOAD_CHARS || !body.startsWith("{")) return body
        return """{"schema_version":$SCHEMA_VERSION,""" + body.substring(1)
    }

    private fun reject(reason: String) = ParseResult.Rejected(reason)
}
