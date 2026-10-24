package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class EffectsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun saveLoadRoundTripLeavesNoTemporaryFiles() {
        val dir = temporary.newFolder()
        val first = Effects.KNOWN + ("colors" to false)
        Effects.save(dir, first)
        assertEquals(first, Effects.load(dir, "colors=on"))
        val next = first + ("idle_glance" to false)
        Effects.save(dir, next)
        assertEquals(next, Effects.load(dir, null))
        assertEquals(first, Effects.parse(File(dir, "effects.txt.lkg").readText(), mutableListOf()))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun corruptPrivateFileFallsBackToLastKnownGoodWithOneWarning() {
        val dir = temporary.newFolder()
        val good = Effects.KNOWN + ("colors" to false)
        Effects.save(dir, good)
        Effects.save(dir, Effects.KNOWN)
        File(dir, "effects.txt").writeText("colors=on\nunknown=off\nidle_glance=bad")
        val warnings = mutableListOf<String>()
        assertEquals(good, Effects.load(dir, "colors=on") { warnings += it })
        assertEquals(1, warnings.size)
    }

    @Test fun corruptBackupFallsBackToAssetThenDefaults() {
        val dir = temporary.newFolder()
        File(dir, "effects.txt").writeText("colors=bad")
        File(dir, "effects.txt.lkg").writeText("unknown=off")
        assertEquals(Effects.KNOWN + ("colors" to false), Effects.load(dir, "colors=off"))
        assertEquals(Effects.KNOWN, Effects.load(dir, null))
        assertEquals(Effects.KNOWN, Effects.load(dir, "colors=bad"))
    }

    @Test fun failedSaveLeavesNoTemporaryFiles() {
        val dir = temporary.newFolder()
        val destination = File(dir, "effects.txt")
        assertTrue(destination.mkdir())
        File(destination, "blocker").writeText("prevent replacement")
        assertTrue(runCatching { Effects.save(dir, Effects.KNOWN) }.isFailure)
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    private val errors = mutableListOf<String>()

    @Test fun intensityDefaultsOnAndCanBeDisabled() {
        assertTrue(Effects.KNOWN.getValue("intensity"))
        assertFalse(Effects.parse("intensity=off", errors).getValue("intensity"))
        assertEquals(Effects.KNOWN + ("intensity" to false), Effects.merge(Effects.KNOWN, "intensity=off", errors))
        assertTrue(errors.isEmpty())
    }

    @Test fun shippedAssetIncludesIntensity() {
        assertEquals(Effects.KNOWN, Effects.parse(File("src/main/assets/effects.txt").readText(), errors))
        assertTrue(errors.isEmpty())
    }

    @Test fun validSwitches() {
        val parsed = Effects.parse("colors=off\nidle_glance=on\nwarmup_glance=off", errors)
        assertEquals(Effects.KNOWN + mapOf("colors" to false, "warmup_glance" to false), parsed)
        assertTrue(errors.isEmpty())
    }

    @Test fun commentsBlankLinesAndWhitespace() {
        val parsed = Effects.parse(" # switches\n\n colors = off # quiet\n idle_glance = on \n", errors)
        assertFalse(parsed.getValue("colors"))
        assertTrue(parsed.getValue("idle_glance"))
        assertTrue(errors.isEmpty())
    }

    @Test fun unknownNameIsSkipped() {
        assertEquals(Effects.KNOWN, Effects.parse("unknown=off", errors))
        assertEquals(listOf("line 1: unknown switch unknown"), errors)
    }

    @Test fun badValueIsSkipped() {
        assertEquals(Effects.KNOWN, Effects.parse("colors=yes", errors))
        assertEquals(listOf("line 1: colors must be on|off"), errors)
    }

    @Test fun duplicateIsSkipped() {
        val parsed = Effects.parse("colors=off\ncolors=on", errors)
        assertFalse(parsed.getValue("colors"))
        assertEquals(listOf("line 2: colors repeated"), errors)
    }

    @Test fun omittedSwitchesKeepDefaults() {
        assertEquals(Effects.KNOWN, Effects.parse("", errors))
        val parsed = Effects.parse("colors=off", errors)
        assertTrue(parsed.getValue("idle_glance"))
        assertTrue(parsed.getValue("warmup_glance"))
        assertTrue(parsed.getValue("intensity"))
        assertTrue(errors.isEmpty())
    }

    @Test fun malformedLineIsSkipped() {
        assertEquals(Effects.KNOWN, Effects.parse("colors\ncolors=off=on", errors))
        assertEquals(2, errors.size)
    }

    @Test fun mergeKeepsCurrentValuesAndAcceptsBothSeparators() {
        val current = Effects.KNOWN + ("warmup_glance" to false)
        assertEquals(Effects.KNOWN + mapOf("colors" to false, "idle_glance" to false, "warmup_glance" to false),
            Effects.merge(current, "colors=off,idle_glance=off\nwarmup_glance=off", errors))
        assertEquals(Effects.KNOWN + ("warmup_glance" to false), current)
        assertTrue(errors.isEmpty())
    }

    @Test fun invalidMergeChangesNothing() {
        val current = Effects.KNOWN + ("colors" to false)
        for (changes in listOf("colors=on,unknown=off", "colors=on,idle_glance=no", "colors=on,colors=off", "colors")) {
            errors.clear()
            assertNull(Effects.merge(current, changes, errors))
            assertFalse(current.getValue("colors"))
            assertTrue(errors.isNotEmpty())
        }
    }

    @Test fun emptyMergeKeepsCurrentAndFillsDefaults() {
        assertEquals(Effects.KNOWN + ("colors" to false), Effects.merge(mapOf("colors" to false), "", errors))
        assertTrue(errors.isEmpty())
    }
}
