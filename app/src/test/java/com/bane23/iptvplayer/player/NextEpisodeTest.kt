package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeTest {

    @Test
    fun `card is hidden until the last seconds`() {
        assertNull(NextEpisode.secondsLeft(positionMs = 0, durationMs = 2_700_000))
        assertNull(NextEpisode.secondsLeft(positionMs = 2_700_000 - NextEpisode.CARD_MS - 1, durationMs = 2_700_000))
    }

    @Test
    fun `seconds left are rounded up and never negative`() {
        assertEquals(20, NextEpisode.secondsLeft(2_680_000, 2_700_000))
        assertEquals(8, NextEpisode.secondsLeft(2_692_500, 2_700_000))
        assertEquals(1, NextEpisode.secondsLeft(2_699_999, 2_700_000))
        assertEquals(0, NextEpisode.secondsLeft(2_700_000, 2_700_000))
        assertEquals(0, NextEpisode.secondsLeft(2_701_000, 2_700_000))
    }

    @Test
    fun `unknown duration never shows the card`() {
        assertNull(NextEpisode.secondsLeft(5_000, 0))
    }

    @Test
    fun `confirmation is needed after several automatic episodes`() {
        assertFalse(NextEpisode.needsConfirmation(0))
        assertFalse(NextEpisode.needsConfirmation(NextEpisode.STILL_WATCHING_AFTER - 1))
        assertTrue(NextEpisode.needsConfirmation(NextEpisode.STILL_WATCHING_AFTER))
    }
}
