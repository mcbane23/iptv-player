package com.bane23.iptvplayer.ui.common.player

import com.bane23.iptvplayer.ui.common.vod.pauseMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseInfoTest {

    @Test
    fun `minutes left are rounded up`() {
        assertEquals(48, PauseTime.minutesLeft(positionMs = 64 * 60_000L + 1_000L, durationMs = 112 * 60_000L))
        assertEquals(1, PauseTime.minutesLeft(positionMs = 0, durationMs = 1_000L))
        assertEquals(90, PauseTime.minutesLeft(positionMs = 0, durationMs = 90 * 60_000L))
    }

    @Test
    fun `unknown duration or finished content has no time left`() {
        assertNull(PauseTime.minutesLeft(positionMs = 5_000L, durationMs = 0))
        assertNull(PauseTime.minutesLeft(positionMs = 10_000L, durationMs = 10_000L))
        assertNull(PauseTime.minutesLeft(positionMs = 12_000L, durationMs = 10_000L))
    }

    @Test
    fun `meta keeps only what exists`() {
        assertEquals(listOf("★ 7.4", "2024", "Drama"), pauseMeta("7.4", "2024-03-01", " Drama "))
        assertEquals(listOf("Drama"), pauseMeta("0", "", "Drama"))
        assertEquals(emptyList<String>(), pauseMeta(null, "n/a", " "))
        assertEquals(listOf("2019"), pauseMeta("", "2019", null))
    }
}
