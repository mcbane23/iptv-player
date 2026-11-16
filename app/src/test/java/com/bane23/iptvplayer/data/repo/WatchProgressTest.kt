package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {
    private val hour = 3_600_000L

    @Test
    fun `end is reached at 95 percent`() {
        assertTrue(WatchProgress.reachedEnd(positionMs = 95 * hour / 100, durationMs = hour))
        assertFalse(WatchProgress.reachedEnd(positionMs = 94 * hour / 100, durationMs = hour))
        assertFalse(WatchProgress.reachedEnd(positionMs = 0, durationMs = 0))
    }

    @Test
    fun `watched mark does not depend on position`() {
        val marked = WatchProgress(itemId = 1, positionMs = 0, durationMs = 0, watched = true)
        assertTrue(marked.finished)
        assertFalse(marked.resumable)
    }

    @Test
    fun `watched item that is watched again can be resumed`() {
        val again = WatchProgress(itemId = 1, positionMs = hour / 2, durationMs = hour, watched = true)
        assertTrue(again.finished)
        assertTrue(again.resumable)
    }

    @Test
    fun `item at the end is not resumable`() {
        val atEnd = WatchProgress(itemId = 1, positionMs = hour, durationMs = hour)
        assertFalse(atEnd.resumable)
        assertFalse(atEnd.finished)
    }
}
