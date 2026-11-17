package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtShiftTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        Prvi red, kaže: 12:30:00,000

        2
        00:59:59,750 --> 01:00:02,000
        Čekaj --> ovo nije vreme
    """.trimIndent()

    @Test
    fun `positive offset moves every cue later`() {
        val shifted = SrtShift.shift(srt, 1_500)
        assertEquals("00:00:02,500 --> 00:00:05,000", shifted.lines()[1])
        assertEquals("01:00:01,250 --> 01:00:03,500", shifted.lines()[5])
    }

    @Test
    fun `text lines are left alone`() {
        val shifted = SrtShift.shift(srt, 1_500)
        assertEquals("Prvi red, kaže: 12:30:00,000", shifted.lines()[2])
        assertEquals("1", shifted.lines()[0])
    }

    @Test
    fun `negative offset does not go below zero`() {
        val shifted = SrtShift.shift(srt, -2_000)
        assertEquals("00:00:00,000 --> 00:00:01,500", shifted.lines()[1])
        assertEquals("00:59:57,750 --> 01:00:00,000", shifted.lines()[5])
    }

    @Test
    fun `zero offset returns the same text`() = assertEquals(srt, SrtShift.shift(srt, 0))

    @Test
    fun `dot separator and short fraction are understood`() =
        assertEquals("00:00:02,500 --> 00:00:03,000", SrtShift.shift("00:00:01.5 --> 00:00:02.000", 1_000))
}
