package com.bane23.iptvplayer.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensaverTimeoutTest {
    @Test
    fun `nothing stored gives the default`() {
        assertEquals(ScreensaverTimeout.DEFAULT, ScreensaverTimeout.fromStored(null))
    }

    @Test
    fun `stored choice is kept, including off`() {
        assertEquals(ScreensaverTimeout.OFF, ScreensaverTimeout.fromStored(0))
        assertEquals(10, ScreensaverTimeout.fromStored(10))
    }

    @Test
    fun `value that is no longer offered falls back to the default`() {
        assertEquals(ScreensaverTimeout.DEFAULT, ScreensaverTimeout.fromStored(7))
        assertEquals(ScreensaverTimeout.DEFAULT, ScreensaverTimeout.fromStored(-1))
    }
}
