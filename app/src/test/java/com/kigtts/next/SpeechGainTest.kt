package com.kigtts.next

import com.kigtts.next.audio.*
import org.junit.Assert.*
import org.junit.Test

class SpeechGainTest {
    private fun pcm(vararg values: Int) = values.flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray()
    @Test fun gainAmplifiesAndSaturatesPcmWithoutChangingProviderBytes() {
        val input = pcm(-32768, -5000, 0, 5000, 32767)
        assertArrayEquals(pcm(-32768, -32768, 0, 32767, 32767), speechPcmGain(input, 10f))
        assertArrayEquals(pcm(-16384, -2500, 0, 2500, 16384), speechPcmGain(input, .5f))
        assertArrayEquals(pcm(-32768, -5000, 0, 5000, 32767), input)
        assertSame(input, speechPcmGain(input, 1f))
        assertArrayEquals(ByteArray(input.size), speechPcmGain(input, 0f))
    }
    @Test fun neutralIsHalfwayAndTheTwoUnequalValueRangesRemainContinuous() {
        assertEquals(0f, speechGainAt(0f)); assertEquals(1f, speechGainAt(.5f)); assertEquals(10f, speechGainAt(1f))
        assertEquals(.5f, speechGainPosition(1f)); assertEquals(1f, speechGainAt(.51f))
        for (gain in listOf(0f, .2f, .8f, 1f, 2f, 5f, 10f)) assertEquals(gain, speechGainAt(speechGainPosition(gain)), .00001f)
        assertTrue(speechGainAt(.6f) > 1f)
    }
}
