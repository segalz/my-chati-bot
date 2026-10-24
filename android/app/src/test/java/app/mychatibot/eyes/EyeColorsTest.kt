package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EyeColorsTest {
    private val table = ColorTable.DEFAULT

    private fun valid(text: String): ColorTable {
        val errors = mutableListOf<String>()
        val parsed = ColorTable.parse(text, errors)
        assertNotNull(errors.toString(), parsed)
        assertTrue(errors.toString(), errors.isEmpty())
        return parsed!!
    }

    private fun invalid(text: String) {
        val errors = mutableListOf<String>()
        assertNull(text, ColorTable.parse(text, errors))
        assertTrue(text, errors.isNotEmpty())
    }

    @Test fun shippedAssetParsesAndEqualsDefault() {
        val text = File("src/main/assets/colors.txt").readText()
        assertEquals(table, valid(text))
        assertEquals(text, valid(text).text)
        assertEquals(0x00BFFF, table.waiting)
        assertEquals(0x00E676, table.speaking)
        assertEquals(300, table.fadeMs)
        assertEquals(7, table.tint.size)
        assertEquals(mapOf(Expression.ANGRY to 0xFF2A2A), table.strong)
    }

    @Test fun customValuesWhitespaceCommentsAndVerbatimText() {
        val text = ("# owner palette\n\n" + table.text)
            .replace("waiting=#00BFFF", " waiting = #aAbBcC ")
            .replace("tint_min=0.15", "tint_min=0")
            .replace("tint_max=0.55", "tint_max=1")
            .replace("fade_ms=300", "fade_ms=5000")
            .replace("gap_low=10.3", "gap_low=-5")
            .replace("gap_high=20.1", "gap_high=5")
            .replace('\n'.toString(), "\r\n")
        val custom = valid(text)
        assertEquals(text, custom.text)
        assertEquals(0xAABBCC, custom.waiting)
        assertEquals(5000, custom.fadeMs)
        assertEquals(0.5f, custom.strength(0f), 0.000001f)
    }

    @Test fun optionalEmotionEntriesAndNeutralAreAccepted() {
        val noHappy = valid(table.text.replace("tint HAPPY=#FFD54F\n", "") + "tint NEUTRAL=#123456\n")
        assertEquals(noHappy.waiting, noHappy.colorFor(false, Expression.HAPPY, 1f))
        assertNotEquals(noHappy.waiting, noHappy.colorFor(false, Expression.NEUTRAL, 1f))
    }

    @Test fun rejectsUnknownKeysLabelsMalformedLinesAndDuplicates() {
        for (line in listOf(
            "unknown=1", "tint UNKNOWN=#123456", "strong NONE=#123456", "tint happy=#123456",
            "tint=#123456", "tint HAPPY EXTRA=#123456", "waiting=#123456", "tint HAPPY=#123456",
            "tint   HAPPY=#123456", "strong ANGRY=#123456", "not-an-assignment", "waiting=#123456=bad",
        )) invalid(table.text + line + "\n")
        invalid("")
        for (key in listOf("waiting", "speaking", "tint_min", "tint_max", "fade_ms", "gap_low", "gap_high")) {
            invalid(table.text.lines().filterNot { it.startsWith("$key=") }.joinToString("\n"))
        }
    }

    @Test fun rejectsBadColorsAndNumbersAsAWhole() {
        for (color in listOf("red", "00BFFF", "#123", "#12345678", "#GG0000", "")) {
            invalid(table.text.replace("waiting=#00BFFF", "waiting=$color"))
            invalid(table.text.replace("tint SAD=#3D5AFE", "tint SAD=$color"))
            invalid(table.text.replace("strong ANGRY=#FF2A2A", "strong ANGRY=$color"))
        }
        for ((key, original, bad) in listOf(
            Triple("tint_min", "0.15", "-0.01"), Triple("tint_max", "0.55", "1.01"),
            Triple("tint_min", "0.15", "0.56"), Triple("tint_max", "0.55", "0.14"),
            Triple("fade_ms", "300", "-1"), Triple("fade_ms", "300", "5001"),
            Triple("fade_ms", "300", "1.5"), Triple("gap_low", "10.3", "20.1"),
            Triple("gap_high", "20.1", "10.2"), Triple("gap_high", "20.1", "NaN"),
            Triple("gap_low", "10.3", "-Infinity"), Triple("tint_min", "0.15", "NaN"),
            Triple("tint_max", "0.55", "Infinity"), Triple("gap_high", "20.1", "1e100"),
        )) invalid(table.text.replace("$key=$original", "$key=$bad"))
    }

    @Test fun inclusiveTintAndFadeBoundaries() {
        val zero = valid(table.text.replace("tint_min=0.15", "tint_min=0")
            .replace("tint_max=0.55", "tint_max=0").replace("fade_ms=300", "fade_ms=0"))
        assertEquals(zero.waiting, zero.colorFor(false, Expression.HAPPY, 1f))
        val one = valid(table.text.replace("tint_min=0.15", "tint_min=1").replace("tint_max=0.55", "tint_max=1"))
        assertEquals(0xFFD54F, one.colorFor(false, Expression.HAPPY, 0f))
    }

    @Test fun strengthMapsGapClampsAndDefaultsUnknownToFull() {
        assertEquals(0f, table.strength(table.gapLow), 0f)
        assertEquals(1f, table.strength(table.gapHigh), 0f)
        assertEquals(0.5f, table.strength((table.gapLow + table.gapHigh) / 2f), 0.000001f)
        assertEquals(0f, table.strength(-100f), 0f)
        assertEquals(1f, table.strength(100f), 0f)
        assertEquals(1f, table.strength(null), 0f)
        assertEquals(1f, table.strength(Float.NaN), 0f)
        assertEquals(0f, table.strength(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(1f, table.strength(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun baseColorsByState() {
        assertEquals(table.waiting, table.colorFor(false, null, 0.5f))
        assertEquals(table.speaking, table.colorFor(true, null, 0.5f))
        assertEquals(table.speaking, table.colorFor(true, Expression.NEUTRAL, 0.5f))
        assertEquals(table.waiting, table.colorFor(false, Expression.NONE, 1f))
    }

    @Test fun midpointExactlyMatchesOldTintForEveryEmotionAndState() {
        for (speaking in listOf(false, true)) {
            val base = if (speaking) table.speaking else table.waiting
            for ((expression, color) in table.tint) {
                assertEquals(EyeColors.blend(base, color, 0.35f), table.colorFor(speaking, expression, 0.5f))
                assertEquals(EyeColors.blend(base, color, 0.15f), table.colorFor(speaking, expression, 0f))
                assertEquals(EyeColors.blend(base, color, 0.55f), table.colorFor(speaking, expression, 1f))
            }
            for (strength in listOf(0f, 0.5f, 1f)) {
                assertEquals(0xFF2A2A, table.colorFor(speaking, Expression.ANGRY, strength))
            }
        }
    }

    @Test fun blendEnds() {
        assertEquals(0x102030, EyeColors.blend(0x102030, 0xFFFFFF, 0f))
        assertEquals(0xFFFFFF, EyeColors.blend(0x102030, 0xFFFFFF, 1f))
        assertEquals(0x808080, EyeColors.blend(0x000000, 0xFFFFFF, 0.5f))
    }
}
