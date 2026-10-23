package app.mychatibot.eyes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineTimingTest {

    @Test fun startsFollowLetters() {
        // weights: 6+4=10, 16+4=20, 6+4=10 → 40
        val s = LineTiming.starts(listOf("aaaaaa", "bbbbbbbbbbbbbbbb", "cccccc"), 4_000)
        assertArrayEquals(longArrayOf(0, 1_000, 3_000), s)
    }

    @Test fun startsHandleEmptyAndNoSpeech() {
        assertEquals(0, LineTiming.starts(emptyList(), 100).size)
        assertArrayEquals(longArrayOf(0, 0), LineTiming.starts(listOf("a", "b"), 0))
        assertArrayEquals(longArrayOf(0, 50), LineTiming.starts(listOf("!", "?"), 100))  // pause weights only
    }

    @Test fun englishMapping() {
        val he = listOf("א", "ב", "ג", "ד")
        assertEquals(listOf("A", "B", "C", "D"), LineTiming.englishFor(he, listOf("A", "B", "C", "D")))
        assertEquals(listOf("A", "A", "B", "B"), LineTiming.englishFor(he, listOf("A", "B")))
        assertEquals(listOf("A", "C", "E", "G"), LineTiming.englishFor(he, listOf("A", "B", "C", "D", "E", "F", "G", "H")))
        assertTrue(LineTiming.englishFor(he, emptyList()).isEmpty())
    }

    @Test fun hebrewDetection() {
        assertTrue(LineTiming.isHebrew("שלום world"))
        assertFalse(LineTiming.isHebrew("Hello, world!"))
    }

    @Test fun estimateUsesSpeakingRate() {
        // (6 + 4) + (16 + 4) = 30 weights at 10 letters/s = 3 s
        assertEquals(3L * 24_000, LineTiming.estimatedFrames(listOf("aaaaaa", "bbbbbbbbbbbbbbbb"), 24_000))
    }
}
