package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTableTest {

    @Test fun parsesLinesAndKeepsDefaultsForTheRest() {
        val errors = mutableListOf<String>()
        val table = VoiceTable.parse("# comment\nHAPPY speaker=12 speed=1.2  # bright\n\nSAD speaker=3 speed=0.8\n", errors)
        assertTrue(errors.isEmpty())
        assertEquals(VoiceStyle(12, 1.2f), table.styleFor(Expression.HAPPY))
        assertEquals(VoiceStyle(3, 0.8f), table.styleFor(Expression.SAD))
        assertEquals(VoiceTable.DEFAULT.styleFor(Expression.ANGRY), table.styleFor(Expression.ANGRY))
    }

    @Test fun noneUnknownAndFailedUseNeutral() {
        val table = VoiceTable.parse("NEUTRAL speaker=5 speed=1.0", mutableListOf())
        assertEquals(VoiceStyle(5, 1.0f), table.styleFor(null))
        assertEquals(VoiceStyle(5, 1.0f), table.styleFor(Expression.NONE))
        assertEquals(VoiceStyle(5, 1.0f), table.styleFor(Expression.WINK_LEFT))  // not listed anywhere
    }

    @Test fun rejectsBadLines() {
        val errors = mutableListOf<String>()
        val table = VoiceTable.parse(
            "HAPPY speaker=1 speed=3.0\nNONE speaker=0 speed=1.0\nJOY speaker=0 speed=1.0\nSAD speed=0.8\n" +
                "ANGRY speaker=1 speed=1.0\nANGRY speaker=2 speed=1.0\nBORED speaker=99999 speed=1.0",
            errors,
        )
        assertEquals(6, errors.size)
        assertEquals(VoiceTable.DEFAULT.styleFor(Expression.HAPPY), table.styleFor(Expression.HAPPY))
        assertEquals(VoiceStyle(1, 1.0f), table.styleFor(Expression.ANGRY))  // first one kept
    }

    @Test fun shippedAssetIsValid() {
        val errors = mutableListOf<String>()
        val text = java.io.File("src/main/assets/voice.txt").readText()
        val table = VoiceTable.parse(text, errors)
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(VoiceStyle(0, 0.85f), table.styleFor(Expression.SAD))
    }
}
