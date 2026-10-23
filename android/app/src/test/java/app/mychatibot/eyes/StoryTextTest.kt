package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTextTest {

    @Test fun splitsAtSentenceEnds() {
        assertEquals(
            listOf("I lost my keys.", "Then I found them!", "Can you believe it?"),
            StoryText.split("I lost my keys. Then I found them!  Can you believe it?"),
        )
    }

    @Test fun keepsQuotesEllipsesAndLines() {
        assertEquals(
            listOf("He said \"stop!\"", "Well...", "maybe", "Ok."),
            StoryText.split("He said \"stop!\" Well... maybe\nOk."),
        )
    }

    @Test fun doesNotSplitAbbreviationsOrNumbers() {
        assertEquals(
            listOf("Dr. Cohen paid 3.5 dollars, e.g. for tea.", "Fine."),
            StoryText.split("Dr. Cohen paid 3.5 dollars, e.g. for tea. Fine."),
        )
    }

    @Test fun dropsEmptyPiecesAndBoundsOutput() {
        assertTrue(StoryText.split(" ... \n \"\" ").isEmpty())
        assertEquals(StoryText.MAX_SENTENCES, StoryText.split("Hi. ".repeat(100)).size)
        val long = StoryText.split("word ".repeat(200))
        assertTrue(long.size > 1 && long.all { it.length <= StoryText.MAX_SENTENCE_BYTES })
        // Hebrew letters take 2 bytes each: 200 of them must be cut below the byte limit.
        val wide = StoryText.split("\u05d0".repeat(200))
        assertTrue(wide.size == 2 && wide.all { StoryText.charsWithin(it, StoryText.MAX_SENTENCE_BYTES) == it.length })
    }

    @Test fun spokenTimeFollowsWordCount() {
        assertEquals(StoryText.MIN_SPOKEN_MS, StoryText.spokenMs("Wow!"))
        // 10 words at 2.5 words/s = 4 s, plus the pause
        assertEquals(4_000L + StoryText.PAUSE_MS, StoryText.spokenMs("one two three four five six seven eight nine ten."))
        assertEquals(2_000L + StoryText.PAUSE_MS, StoryText.spokenMs("one two three four five six seven eight nine ten.", 5f))
    }

    @Test fun splitsAfterTypographicQuotes() {
        assertEquals(listOf("“I am sad.”", "Then I am happy!"), StoryText.split("“I am sad.” Then I am happy!"))
    }
}
