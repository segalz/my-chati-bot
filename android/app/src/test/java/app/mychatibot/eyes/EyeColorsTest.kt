package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EyeColorsTest {

    @Test fun baseColorsByState() {
        assertEquals(EyeColors.WAITING, EyeColors.colorFor(speaking = false, expression = null))
        assertEquals(EyeColors.SPEAKING, EyeColors.colorFor(speaking = true, expression = null))
        assertEquals(EyeColors.SPEAKING, EyeColors.colorFor(speaking = true, expression = Expression.NEUTRAL))
    }

    @Test fun mildEmotionTintsAndAngerIsRed() {
        val happy = EyeColors.colorFor(speaking = true, expression = Expression.HAPPY)
        assertNotEquals(EyeColors.SPEAKING, happy)
        assertEquals(EyeColors.blend(EyeColors.SPEAKING, 0xFFD54F, EyeColors.TINT_AMOUNT), happy)
        assertEquals(0xFF2A2A, EyeColors.colorFor(speaking = true, expression = Expression.ANGRY))
        assertEquals(0xFF2A2A, EyeColors.colorFor(speaking = false, expression = Expression.ANGRY))
    }

    @Test fun blendEnds() {
        assertEquals(0x102030, EyeColors.blend(0x102030, 0xFFFFFF, 0f))
        assertEquals(0xFFFFFF, EyeColors.blend(0x102030, 0xFFFFFF, 1f))
        assertEquals(0x808080, EyeColors.blend(0x000000, 0xFFFFFF, 0.5f))
    }
}
