package com.bane23.iptvplayer.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenDimTest {
    @Test
    fun `nothing stored means off`() {
        assertEquals(ScreenDim.OFF, ScreenDim.fromStored(null))
    }

    @Test
    fun `stored choice is kept`() {
        assertEquals(40, ScreenDim.fromStored(40))
        assertEquals(80, ScreenDim.fromStored(80))
    }

    @Test
    fun `value that is not offered falls back to off`() {
        assertEquals(ScreenDim.OFF, ScreenDim.fromStored(35))
        assertEquals(ScreenDim.OFF, ScreenDim.fromStored(100))
        assertEquals(ScreenDim.OFF, ScreenDim.fromStored(-20))
    }
}
