package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PromptBundleTest {

    private fun bundle(header: String, prompt: String = "You control the eyes.") = "$header\n---\n$prompt\n"

    @Test fun parsesValidBundleAndKeepsPromptVerbatim() {
        val errors = mutableListOf<String>()
        val b = PromptBundle.parse(bundle("# c\nversion=5\nlabels=NEUTRAL, HAPPY ,NONE", "Line 1\n\nLine 3"), errors)
        assertNotNull(errors.toString(), b)
        assertEquals(5, b!!.version)
        assertEquals(listOf("NEUTRAL", "HAPPY", "NONE"), b.labels)
        assertEquals("Line 1\n\nLine 3", b.prompt)
    }

    @Test fun crlfIsNormalized() {
        val b = PromptBundle.parse("version=4\r\nlabels=NEUTRAL,NONE\r\n---\r\nA\r\nB\r\n", mutableListOf())
        assertEquals("A\nB", b!!.prompt)
    }

    @Test fun rejectsBadBundles() {
        fun bad(text: String) = mutableListOf<String>().let { e -> assertNull(text, PromptBundle.parse(text, e)); e }
        bad("version=4\nlabels=NEUTRAL,NONE\nA")                       // no ---
        bad(bundle("labels=NEUTRAL,NONE"))                              // no version
        bad(bundle("version=4"))                                        // no labels
        bad(bundle("version=4\nlabels=NEUTRAL,LOVEX,NONE"))             // unknown label
        bad(bundle("version=4\nlabels=NEUTRAL,HAPPY,HAPPY,NONE"))       // repeated
        bad(bundle("version=4\nlabels=HAPPY,NONE"))                     // no NEUTRAL
        bad(bundle("version=4\nlabels=NEUTRAL,NONE\ncolor=red"))        // unknown header
        bad(bundle("version=4\nlabels=NEUTRAL,NONE", "   "))            // empty prompt
        bad(bundle("version=4\nlabels=NEUTRAL,NONE", "x".repeat(PromptBundle.MAX_PROMPT_BYTES + 1)))
        bad(bundle("version=4\nlabels=NEUTRAL,,NONE"))                // empty field
        bad(bundle("version=4\nlabels=NEUTRAL,NONE", "א".repeat(PromptBundle.MAX_PROMPT_BYTES / 2 + 1)))  // bytes, not chars
        bad("x".repeat(PromptBundle.MAX_FILE_CHARS + 1))
    }

    @Test fun shippedV4IsValidAndMatchesTheNativeDefault() {
        val text = File("src/main/assets/bundles/v4.txt").readText()
        val errors = mutableListOf<String>()
        val b = PromptBundle.parse(text, errors)
        assertTrue(errors.toString(), b != null)
        assertEquals(4, b!!.version)
        assertEquals(listOf("NEUTRAL", "HAPPY", "SAD", "ANGRY", "SURPRISED", "SLEEPY", "BORED", "WINK_LEFT", "WINK_RIGHT", "NONE"), b.labels)
        // byte for byte the prompt compiled into eye_brain.cpp (the 88/100 baseline)
        val cpp = File("src/main/cpp/eye_brain.cpp").readText().replace("\r\n", "\n")
        val native = Regex("""kSystemPrompt = R"\((.*?)\)";""", RegexOption.DOT_MATCHES_ALL).find(cpp)!!.groupValues[1]
        assertEquals(native, b.prompt)
    }
}
