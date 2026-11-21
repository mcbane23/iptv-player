package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRateMeterTest {
    private fun feed(meter: FrameRateMeter, gapUs: Long, frames: Int, startUs: Long = 0L): Long {
        var time = startUs
        repeat(frames) {
            meter.onFrame(time)
            time += gapUs
        }
        return time
    }

    @Test
    fun `unknown until enough frames arrive`() {
        val meter = FrameRateMeter()
        assertEquals(0f, meter.frameRate())
        feed(meter, 40_000, 5)
        assertEquals(0f, meter.frameRate())
    }

    @Test
    fun `measures common rates exactly`() {
        assertEquals(25f, FrameRateMeter().also { feed(it, 40_000, 60) }.frameRate())
        assertEquals(50f, FrameRateMeter().also { feed(it, 20_000, 60) }.frameRate())
        assertEquals(29.97f, FrameRateMeter().also { feed(it, 33_367, 60) }.frameRate())
        assertEquals(23.976f, FrameRateMeter().also { feed(it, 41_708, 60) }.frameRate())
        assertEquals(24f, FrameRateMeter().also { feed(it, 41_667, 60) }.frameRate())
    }

    @Test
    fun `uncommon rate is kept as measured`() {
        assertEquals(15f, FrameRateMeter().also { feed(it, 66_667, 60) }.frameRate(), 0.01f)
    }

    @Test
    fun `dropped frames and seeks do not change the result`() {
        val meter = FrameRateMeter()
        var time = feed(meter, 40_000, 20)
        // Tri ispuštene slike: razmak je četiri puta veći.
        time = feed(meter, 40_000, 20, startUs = time + 120_000)
        // Premotavanje unapred, pa unazad.
        feed(meter, 40_000, 20, startUs = time + 90_000_000)
        feed(meter, 40_000, 20, startUs = 5_000_000)
        assertEquals(25f, meter.frameRate())
    }

    @Test
    fun `reset forgets the old stream`() {
        val meter = FrameRateMeter()
        feed(meter, 40_000, 60)
        meter.reset()
        assertEquals(0f, meter.frameRate())
        feed(meter, 20_000, 60)
        assertEquals(50f, meter.frameRate())
    }
}
