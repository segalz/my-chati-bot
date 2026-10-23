package app.mychatibot.eyes

/** Reads 16-bit PCM WAV (as written by Android's TextToSpeech.synthesizeToFile) into float samples. */
object WavPcm {
    class Audio(val samples: FloatArray, val sampleRate: Int)

    /**
     * Mono or stereo 16-bit PCM (stereo is mixed down); null for anything else or a broken file.
     * Chunk sizes are checked with 64-bit arithmetic. Only the data chunk may claim more bytes than
     * the file holds (some engines leave a placeholder length while streaming); its samples are then
     * read up to the end of the file, whole frames only.
     */
    fun parse(bytes: ByteArray): Audio? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var pos = 12L
        var rate = 0
        var channels = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val at = pos.toInt()
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = le32(bytes, at + 4).toLong() and 0xffff_ffffL
            val body = (pos + 8).toInt()
            when (id) {
                "fmt " -> {
                    if (size < 16 || body + 16L > bytes.size || le16(bytes, body) != 1) return null  // 1 = PCM
                    channels = le16(bytes, body + 2)
                    rate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                }
                "data" -> {
                    if (bits != 16 || channels !in 1..2 || rate <= 0) return null
                    val end = minOf(bytes.size.toLong(), body + size).toInt()
                    val frames = (end - body) / (2 * channels)
                    val out = FloatArray(frames) { f ->
                        var sum = 0f
                        for (c in 0 until channels) {
                            val i = body + (f * channels + c) * 2
                            sum += ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort() / 32_768f
                        }
                        sum / channels
                    }
                    return Audio(out, rate)
                }
                else -> if (body + size > bytes.size) return null  // a broken chunk before the data
            }
            pos = body + size + (size and 1)  // chunks are padded to an even size
        }
        return null
    }

    private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
    private fun le32(b: ByteArray, i: Int) =
        le16(b, i) or ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24)
}
