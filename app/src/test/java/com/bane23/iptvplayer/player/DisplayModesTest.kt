package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModesTest {

    private val modes = listOf(
        DisplayModeInfo(1, 1920, 1080, 60f),
        DisplayModeInfo(2, 1920, 1080, 50f),
        DisplayModeInfo(3, 1920, 1080, 24f),
        DisplayModeInfo(4, 1920, 1080, 23.976f),
        DisplayModeInfo(5, 1920, 1080, 59.94f),
        DisplayModeInfo(6, 3840, 2160, 50f),
        DisplayModeInfo(7, 1920, 1080, 25f),
    )

    @Test
    fun `25 and 50 fps go to 50 Hz`() {
        assertEquals(2, DisplayModes.pick(modes, currentId = 1, frameRate = 25f))
        assertEquals(2, DisplayModes.pick(modes, currentId = 1, frameRate = 50f))
    }

    @Test
    fun `film rates pick the closest mode`() {
        assertEquals(3, DisplayModes.pick(modes, currentId = 1, frameRate = 24f))
        assertEquals(4, DisplayModes.pick(modes, currentId = 1, frameRate = 23.976f))
    }

    @Test
    fun `30 and 60 fps stay on 60 Hz`() {
        assertNull(DisplayModes.pick(modes, currentId = 1, frameRate = 30f))
        assertNull(DisplayModes.pick(modes, currentId = 1, frameRate = 60f))
    }

    @Test
    fun `a mode that already fits is kept`() {
        assertNull(DisplayModes.pick(modes, currentId = 2, frameRate = 25f))
        assertNull(DisplayModes.pick(modes, currentId = 7, frameRate = 25f))
    }

    @Test
    fun `resolution is never changed`() {
        val only4k50 = listOf(DisplayModeInfo(1, 1920, 1080, 60f), DisplayModeInfo(6, 3840, 2160, 50f))
        assertNull(DisplayModes.pick(only4k50, currentId = 1, frameRate = 25f))
    }

    @Test
    fun `unknown frame rate or mode changes nothing`() {
        assertNull(DisplayModes.pick(modes, currentId = 1, frameRate = 0f))
        assertNull(DisplayModes.pick(modes, currentId = 1, frameRate = Float.NaN))
        assertNull(DisplayModes.pick(modes, currentId = 99, frameRate = 25f))
    }
}
