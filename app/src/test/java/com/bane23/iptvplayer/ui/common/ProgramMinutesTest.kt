package com.bane23.iptvplayer.ui.common

import com.bane23.iptvplayer.ui.common.guide.programMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramMinutesTest {
    private val start = 1_000_000_000L
    private val stop = start + 60 * 60_000L

    @Test
    fun `elapsed is rounded down and left is rounded up`() {
        assertEquals(0 to 60, programMinutes(start, stop, start))
        assertEquals(25 to 35, programMinutes(start, stop, start + 25 * 60_000L))
        assertEquals(25 to 35, programMinutes(start, stop, start + 25 * 60_000L + 30_000L))
        assertEquals(59 to 1, programMinutes(start, stop, stop - 1))
    }

    @Test
    fun `nothing outside the program`() {
        assertNull(programMinutes(start, stop, start - 1))
        assertNull(programMinutes(start, stop, stop))
    }
}
