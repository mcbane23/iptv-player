package com.bane23.iptvplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StereoDownmixTest {

    @After
    fun reset() {
        StereoDownmix.enabled = false
    }

    private fun format(channels: Int) = AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT)

    @Test
    fun inactiveUntilEnabled() {
        val processor = StereoDownmixProcessor()
        processor.configure(format(6))
        assertFalse(processor.isActive)
    }

    @Test
    fun stereoInputIsLeftAlone() {
        StereoDownmix.enabled = true
        val processor = StereoDownmixProcessor()
        processor.configure(format(2))
        assertFalse(processor.isActive)
    }

    @Test
    fun fivePointOneBecomesStereo() {
        StereoDownmix.enabled = true
        val processor = StereoDownmixProcessor()
        val out = processor.configure(format(6))
        assertTrue(processor.isActive)
        assertEquals(2, out.channelCount)
        processor.flush()

        // Jedan okvir: samo levi prednji kanal svira, ostali ćute.
        val input = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
        input.putShort(1000).putShort(0).putShort(0).putShort(0).putShort(0).putShort(0).flip()
        processor.queueInput(input)
        val output = processor.output
        assertEquals(4, output.remaining())
        val left = output.getShort().toInt()
        val right = output.getShort().toInt()
        assertTrue(left in 600..1000)
        assertEquals(0, right)
        assertEquals(0, input.remaining())
    }
}
