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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class LoudnessNormalizerTest {

    private val rate = 48_000

    @After
    fun reset() {
        LoudnessNormalization.level = LoudnessLevel.OFF
    }

    /** Stereo sinus od 440 Hz zadate amplitude, [seconds] dugačak. */
    private fun tone(amplitude: Float, seconds: Float): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * 2) { i -> amplitude * sin(2 * PI * 440 * (i / 2) / rate).toFloat() }
    }

    private fun db(samples: FloatArray, fromFraction: Float = 0.8f): Double {
        val start = (samples.size * fromFraction).toInt()
        var sum = 0.0
        for (i in start until samples.size) sum += samples[i] * samples[i]
        return 20 * log10(sqrt(sum / (samples.size - start)))
    }

    private fun level(amplitude: Float, level: LoudnessLevel, seconds: Float = 30f): FloatArray {
        val samples = tone(amplitude, seconds)
        LoudnessLeveler(rate).process(samples, samples.size / 2, 2, level)
        return samples
    }

    @Test
    fun offLeavesSoundAlone() {
        val original = tone(0.05f, 2f)
        val samples = original.copyOf()
        LoudnessLeveler(rate).process(samples, samples.size / 2, 2, LoudnessLevel.OFF)
        assertTrue(original.contentEquals(samples))
    }

    @Test
    fun quietAndLoudEndUpClose() {
        for (mode in listOf(LoudnessLevel.LIGHT, LoudnessLevel.STRONG)) {
            val quiet = db(level(0.04f, mode)) // oko -30 dB
            val loud = db(level(0.5f, mode)) // oko -9 dB
            assertTrue("$mode: $quiet / $loud", abs(quiet - loud) < 4)
        }
    }

    @Test
    fun boostIsLimited() {
        // Vrlo tih zvuk (oko -51 dB, iznad praga tišine) se pojača najviše za 10 dB, ne do cilja.
        val out = db(level(0.004f, LoudnessLevel.LIGHT))
        assertTrue("$out", out < -38)
    }

    @Test
    fun peaksNeverClip() {
        val samples = level(0.99f, LoudnessLevel.STRONG, seconds = 3f)
        assertTrue(samples.all { abs(it) <= 0.951f })
        // Tih početak pa nagli udar: pojačanje je visoko kad udar stigne, ali vrhovi i dalje ne prelaze granicu.
        val leveler = LoudnessLeveler(rate)
        val quiet = tone(0.02f, 10f)
        leveler.process(quiet, quiet.size / 2, 2, LoudnessLevel.STRONG)
        val burst = tone(0.9f, 1f)
        leveler.process(burst, burst.size / 2, 2, LoudnessLevel.STRONG)
        assertTrue(burst.all { abs(it) <= 0.951f })
    }

    @Test
    fun silenceDoesNotRaiseGain() {
        val leveler = LoudnessLeveler(rate)
        val speech = tone(0.1f, 10f)
        leveler.process(speech, speech.size / 2, 2, LoudnessLevel.LIGHT)
        val before = leveler.currentGain
        val silence = FloatArray(rate * 2 * 10)
        leveler.process(silence, silence.size / 2, 2, LoudnessLevel.LIGHT)
        assertEquals(before, leveler.currentGain, 0.05f)
    }

    @Test
    fun turningOffReturnsToNormal() {
        val leveler = LoudnessLeveler(rate)
        val quiet = tone(0.03f, 10f)
        leveler.process(quiet, quiet.size / 2, 2, LoudnessLevel.LIGHT)
        assertTrue(leveler.currentGain > 2f)
        val more = tone(0.03f, 5f)
        leveler.process(more, more.size / 2, 2, LoudnessLevel.OFF)
        assertEquals(1f, leveler.currentGain, 0f)
    }

    @Test
    fun processorHandles16BitPcm() {
        val processor = LoudnessNormalizerProcessor()
        val format = AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()
        assertTrue(processor.isActive)
        LoudnessNormalization.level = LoudnessLevel.LIGHT
        val samples = tone(0.03f, 20f)
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort((it * 32767).toInt().toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        val out = FloatArray(output.remaining() / 2) { output.getShort() / 32768f }
        assertEquals(samples.size, out.size)
        assertTrue(db(out) > db(samples) + 6)
    }

    @Test
    fun processorSkipsOtherEncodings() {
        val processor = LoudnessNormalizerProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        assertFalse(processor.isActive)
    }
}
