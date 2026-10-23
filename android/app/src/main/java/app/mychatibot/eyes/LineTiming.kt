package app.mychatibot.eyes

/**
 * Timing for a line spoken as one continuous audio stream (the cloud voice, Stage 9): where in the
 * audio each sentence starts, and which English sentence the eye model judges for each spoken one.
 * Pure and deterministic.
 */
object LineTiming {
    /** Extra weight per sentence for the pause after it, in letters. */
    const val PAUSE_WEIGHT = 4

    /**
     * Start frame of each sentence within [totalFrames] of speech, proportional to its letters plus
     * a pause. The first sentence starts at 0; the result is non-decreasing and below [totalFrames]
     * when there is any speech.
     */
    fun starts(sentences: List<String>, totalFrames: Long): LongArray {
        val weights = sentences.map { s -> s.count(Char::isLetterOrDigit) + PAUSE_WEIGHT }
        val total = weights.sum().coerceAtLeast(1)
        val out = LongArray(sentences.size)
        var acc = 0L
        for (i in sentences.indices) {
            out[i] = totalFrames * acc / total
            acc += weights[i]
        }
        return out
    }

    /** Average speaking rate of the Hebrew cloud voice (measured 2026-10-23: ~10 letters/s). */
    const val LETTERS_PER_SECOND = 10

    /** Expected speech length of [sentences] in frames, before the real length is known. */
    fun estimatedFrames(sentences: List<String>, sampleRate: Int): Long {
        val weight = sentences.sumOf { s -> s.count(Char::isLetterOrDigit) + PAUSE_WEIGHT }
        return weight.toLong() * sampleRate / LETTERS_PER_SECOND
    }

    /**
     * The English sentence (for the English-only eye model, R12) that goes with each of [spoken]
     * sentences: one to one when the counts match, otherwise mapped proportionally by position.
     * Empty when there is no English.
     */
    fun englishFor(spoken: List<String>, english: List<String>): List<String> {
        if (english.isEmpty() || spoken.isEmpty()) return emptyList()
        if (english.size == spoken.size) return english
        return spoken.indices.map { i -> english[(i * english.size / spoken.size).coerceAtMost(english.size - 1)] }
    }

    /** Whether [text] contains Hebrew letters (then it is spoken by the Hebrew voice). */
    fun isHebrew(text: String) = text.any { it in 'א'..'ת' }
}
