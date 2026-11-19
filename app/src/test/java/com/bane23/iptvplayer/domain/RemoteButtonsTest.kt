package com.bane23.iptvplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteButtonsTest {

    @Test
    fun `defaults follow the agreed layout`() {
        assertEquals(RemoteAction.LAST_CHANNEL, RemoteButton.RED.defaultAction)
        assertEquals(RemoteAction.SCHEDULE, RemoteButton.GREEN.defaultAction)
        assertEquals(RemoteAction.FAVORITE, RemoteButton.YELLOW.defaultAction)
        assertEquals(RemoteAction.AUDIO, RemoteButton.BLUE.defaultAction)
        assertEquals(RemoteAction.NEXT_CHANNEL, RemoteButton.CHANNEL_UP.defaultAction)
        assertEquals(RemoteAction.PREVIOUS_CHANNEL, RemoteButton.CHANNEL_DOWN.defaultAction)
        assertEquals(RemoteAction.QUICK_SETTINGS, RemoteButton.MENU.defaultAction)
        assertEquals(RemoteAction.CHANNEL_LIST, RemoteButton.LONG_OK.defaultAction)
    }

    @Test
    fun `saved action wins over the default`() {
        assertEquals(RemoteAction.INFO, RemoteAction.of("INFO", RemoteButton.RED))
        assertEquals(RemoteAction.NONE, RemoteAction.of("NONE", RemoteButton.MENU))
    }

    @Test
    fun `missing or unknown saved value falls back to the default`() {
        assertEquals(RemoteAction.LAST_CHANNEL, RemoteAction.of(null, RemoteButton.RED))
        assertEquals(RemoteAction.SCHEDULE, RemoteAction.of("SOMETHING_OLD", RemoteButton.GREEN))
    }

    @Test
    fun `keys map to their buttons`() {
        assertEquals(RemoteButton.RED, RemoteButton.ofKey(183))
        assertEquals(RemoteButton.BLUE, RemoteButton.ofKey(186))
        assertEquals(RemoteButton.CHANNEL_UP, RemoteButton.ofKey(166))
        assertEquals(RemoteButton.CHANNEL_DOWN, RemoteButton.ofKey(167))
        assertEquals(RemoteButton.MENU, RemoteButton.ofKey(82))
        // OK nije taster koji se podešava; dugo OK se prepoznaje po trajanju pritiska.
        assertNull(RemoteButton.ofKey(23))
    }

    @Test
    fun `only channel stepping repeats while held`() {
        assertEquals(
            setOf(RemoteAction.NEXT_CHANNEL, RemoteAction.PREVIOUS_CHANNEL),
            RemoteAction.entries.filter { it.repeats }.toSet(),
        )
    }
}
