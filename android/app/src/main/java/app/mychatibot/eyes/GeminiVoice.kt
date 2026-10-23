package app.mychatibot.eyes

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The cloud voice for Hebrew (Stage 9): Gemini TTS over HTTPS, one streamed request per line.
 * Gemini reads the emotion from the text itself, guided by [style]; the audio arrives as raw 16-bit
 * PCM at [SAMPLE_RATE] and is handed out as float samples while it streams.
 *
 * Only the line's text goes to Google; the eyes never use the network. The API key lives in the
 * app's private files (`files/secrets/gemini_api_key`, put there over the debug channel, never in
 * the APK or the repository). Nothing from the key, the request or the server's replies is logged or
 * shown: failures carry only a fixed category ([Failure.message]).
 */
class GeminiVoice(private val keyFile: File) {

    /** A failure described by a fixed category only (never server text, which could echo secrets). */
    class Failure(category: String) : Exception(category)

    /** One request; [cancel] (any thread) stops it, also while it is blocked reading. */
    class Call {
        @Volatile var cancelled = false
            private set
        @Volatile internal var connection: HttpURLConnection? = null

        fun cancel() {
            cancelled = true
            connection?.disconnect()
        }
    }

    @Volatile var voice = DEFAULT_VOICE
    @Volatile var style = DEFAULT_STYLE

    fun hasKey(): Boolean = keyFile.isFile && keyFile.length() in 20..400

    /**
     * Streams speech for [text], calling [onPcm] with each piece of audio as it arrives; [onPcm]
     * returns false to stop. Returns at the end of the stream or when [call] is cancelled; throws
     * [Failure] otherwise.
     */
    fun stream(text: String, call: Call, onPcm: (FloatArray) -> Boolean) {
        if (call.cancelled) return
        val key = runCatching { keyFile.readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: throw Failure("no key")
        val conn = try {
            URL(ENDPOINT).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw Failure("network error")
        }
        call.connection = conn
        try {
            if (call.cancelled) return
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(requestBody(text, voice, style).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) throw Failure(httpCategory(code))
            var odd: Int? = null  // a byte left over when a chunk ends mid-sample
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (call.cancelled) return
                    if (!line.startsWith("data:")) continue
                    val event = runCatching { JSONObject(line.substring(5).trim()) }.getOrNull() ?: continue
                    if (event.has("error")) throw Failure("stream error")
                    val delta = event.optJSONObject("delta") ?: continue
                    if (delta.optString("type") != "audio") continue
                    var bytes = runCatching { Base64.decode(delta.optString("data"), Base64.DEFAULT) }
                        .getOrElse { throw Failure("bad audio data") }
                    odd?.let { bytes = byteArrayOf(it.toByte()) + bytes }
                    odd = if (bytes.size % 2 == 1) bytes.last().toInt() else null
                    val samples = FloatArray(bytes.size / 2) { i ->
                        val lo = bytes[2 * i].toInt() and 0xff
                        val hi = bytes[2 * i + 1].toInt()
                        ((hi shl 8) or lo).toShort() / 32_768f
                    }
                    if (samples.isNotEmpty() && !onPcm(samples)) return
                }
            }
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            if (call.cancelled) return  // disconnect() during a blocked read
            throw Failure("network error")
        } finally {
            call.connection = null
            conn.disconnect()
        }
    }

    companion object {
        const val SAMPLE_RATE = 24_000
        const val MODEL = "gemini-3.8-flash-tts"
        const val DEFAULT_VOICE = "Kore"
        const val DEFAULT_STYLE =
            "Speak naturally and expressively, like a warm friend telling a story; let each sentence carry its own emotion."
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"

        fun requestBody(text: String, voice: String, style: String): String = JSONObject()
            .put("model", MODEL)
            .put("stream", true)
            .put("input", JSONArray().put(JSONObject()
                .put("type", "user_input")
                .put("content", JSONArray().put(JSONObject()
                    .put("type", "text")
                    .put("text", text)
                    .put("annotations", JSONArray().put(JSONObject()
                        .put("type", "speech_metadata")
                        .put("style", style)))))))
            .put("generation_config", JSONObject()
                .put("speech_config", JSONArray().put(JSONObject().put("voice", voice))))
            .put("response_format", JSONObject().put("type", "audio"))
            .toString()

        fun httpCategory(code: Int): String = when (code) {
            400 -> "HTTP 400 (bad request)"
            401, 403 -> "HTTP $code (key rejected)"
            429 -> "HTTP 429 (rate limit)"
            in 500..599 -> "HTTP $code (server error)"
            else -> "HTTP $code"
        }
    }
}
