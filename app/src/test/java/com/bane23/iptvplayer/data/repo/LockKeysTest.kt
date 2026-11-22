package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockKeysTest {
    @Test
    fun `an item key gives back its kind, playlist and key`() {
        val key = LockRepository.itemKey(LockRepository.KIND_CHANNEL, 3, "tvg|kanal|1")
        assertEquals(Triple(LockRepository.KIND_CHANNEL, 3L, "tvg|kanal|1"), LockRepository.parseItemKey(key))
    }

    @Test
    fun `category keys are not item keys`() {
        assertNull(LockRepository.parseItemKey(LockRepository.liveKey(3, "Sport")))
        assertNull(LockRepository.parseItemKey(LockRepository.vodKey(3, true, "12")))
        assertNull(LockRepository.parseItemKey("item|movie|nije-broj|5"))
    }

    @Test
    fun `locked movie and series ids are read per playlist and kind`() {
        val keys = setOf(
            LockRepository.itemKey(LockRepository.KIND_MOVIE, 1, "10"),
            LockRepository.itemKey(LockRepository.KIND_MOVIE, 1, "11"),
            LockRepository.itemKey(LockRepository.KIND_MOVIE, 2, "12"),
            LockRepository.itemKey(LockRepository.KIND_SERIES, 1, "13"),
            LockRepository.itemKey(LockRepository.KIND_CHANNEL, 1, "14"),
            LockRepository.vodKey(1, false, "15"),
        )
        assertEquals(setOf(10L, 11L), LockRepository.vodItemIds(keys, 1, series = false))
        assertEquals(setOf(13L), LockRepository.vodItemIds(keys, 1, series = true))
        assertEquals(emptySet<Long>(), LockRepository.vodItemIds(keys, 3, series = false))
    }
}
