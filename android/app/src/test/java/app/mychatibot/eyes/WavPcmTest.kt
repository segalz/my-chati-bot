package app.mychatibot.eyes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavPcmTest {

    private fun wav(rate: Int, channels: Int, bits: Int, samples: ShortArray, extraChunk: Boolean = false): ByteArray {
        val data = ByteArrayOutputStream().apply { samples.forEach { write(it.toInt() and 0xff); write(it.toInt() shr 8 and 0xff) } }.toByteArray()
        val out = ByteArrayOutputStream()
        fun s(x: String) = out.write(x.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { for (k in 0..3) out.write(v shr (8 * k) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff) }
        s("RIFF"); i32(0); s("WAVE")
        if (extraChunk) { s("LIST"); i32(3); out.write(byteArrayOf(1, 2, 3)); out.write(0) }  // odd size + pad
        s("fmt "); i32(16); i16(1); i16(channels); i32(rate); i32(rate * channels * 2); i16(channels * 2); i16(bits)
        s("data"); i32(data.size); out.write(data)
        return out.toByteArray()
    }

    @Test fun monoPcm() {
        val a = WavPcm.parse(wav(24_000, 1, 16, shortArrayOf(0, 16_384, -32_768)))!!
        assertEquals(24_000, a.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f), a.samples, 1e-6f)
    }

    @Test fun stereoIsMixedAndOddChunksSkipped() {
        val a = WavPcm.parse(wav(22_050, 2, 16, shortArrayOf(16_384, 0, -16_384, -16_384), extraChunk = true))!!
        assertArrayEquals(floatArrayOf(0.25f, -0.5f), a.samples, 1e-6f)
    }

    @Test fun brokenSizesNeverThrow() {
        val good = wav(24_000, 1, 16, shortArrayOf(1, 2, 3), extraChunk = true)
        // the LIST chunk (at offset 12) claims 0x7fffffff bytes
        val huge = good.copyOf().also { b -> b[16] = 0xff.toByte(); b[17] = 0xff.toByte(); b[18] = 0xff.toByte(); b[19] = 0x7f }
        assertNull(WavPcm.parse(huge))
        // data chunk truncated mid-frame: whole frames only
        val truncated = WavPcm.parse(wav(24_000, 1, 16, shortArrayOf(100, 200, 300)).let { it.copyOf(it.size - 1) })!!
        assertEquals(2, truncated.samples.size)
        for (cut in 0 until good.size) WavPcm.parse(good.copyOf(cut))  // no exception for any prefix
    }

    @Test fun rejectsOtherFormats() {
        assertNull(WavPcm.parse(wav(24_000, 1, 8, shortArrayOf(1))))
        assertNull(WavPcm.parse("not a wav".toByteArray()))
    }
}
