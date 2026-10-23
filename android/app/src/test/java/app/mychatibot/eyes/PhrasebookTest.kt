package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhrasebookTest {

    private val book = Phrasebook.parse(
        """
        # comment
        HAPPY: hello | thank you   # trailing comment
        SLEEPY: good night
        NEUTRAL up: look up
        NONE: ok
        """.trimIndent(),
    )

    @Test fun exactMatchAfterNormalization() {
        assertEquals(Expression.HAPPY to Look.CENTER, book.lookup("Hello!"))
        assertEquals(Expression.HAPPY to Look.CENTER, book.lookup("  thank   YOU.  "))
        assertEquals(Expression.SLEEPY to Look.CENTER, book.lookup("Good night!!"))
        assertEquals(Expression.NEUTRAL to Look.UP, book.lookup("Look up"))
        assertEquals(Expression.NONE to Look.CENTER, book.lookup("OK."))
    }

    @Test fun noFuzzyMatches() {
        assertNull(book.lookup("hello there my friend"))
        assertNull(book.lookup("not hello"))
        assertNull(book.lookup("good nights"))
    }

    @Test fun badLinesAreReportedAndSkipped() {
        val errors = mutableListOf<String>()
        val b = Phrasebook.parse("HAPPY hello\nJOYFUL: yay\nHAPPY sideways: hi\nHAPPY: hi | hi\nSAD: ", errors)
        assertEquals(1, b.size)
        assertEquals(5, errors.size)
    }

    @Test fun shippedPhrasebookParsesCleanly() {
        val errors = mutableListOf<String>()
        val shipped = Phrasebook.parse(File("src/main/assets/phrasebook.txt").readText(), errors)
        assertTrue(errors.toString(), errors.isEmpty())
        assertTrue(shipped.size > 50)
        assertEquals(Expression.SLEEPY to Look.CENTER, shipped.lookup("Good night!"))
    }
}
