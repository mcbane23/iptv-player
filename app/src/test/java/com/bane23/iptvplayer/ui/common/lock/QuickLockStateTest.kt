package com.bane23.iptvplayer.ui.common.lock

import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickLockStateTest {
    private val key = LockRepository.liveKey(1, "Sport")
    private val other = LockRepository.liveKey(1, "Vesti")

    @Test
    fun `without a pin only locking is offered`() {
        assertEquals(listOf(LockAction.LOCK), QuickLockState().actions(key))
    }

    @Test
    fun `while locked a new lock can be added and everything unlocked with the pin`() {
        val state = QuickLockState(hasPin = true, unlocked = false, lockedKeys = setOf(other))
        assertEquals(listOf(LockAction.LOCK, LockAction.UNLOCK_ALL), state.actions(key))
    }

    @Test
    fun `a locked category cannot lose its lock before the pin is entered`() {
        val state = QuickLockState(hasPin = true, unlocked = false, lockedKeys = setOf(key))
        assertEquals(listOf(LockAction.UNLOCK_ALL), state.actions(key))
    }

    @Test
    fun `after the pin the lock can be removed or everything locked again`() {
        val state = QuickLockState(hasPin = true, unlocked = true, lockedKeys = setOf(key))
        assertEquals(listOf(LockAction.REMOVE, LockAction.LOCK_AGAIN), state.actions(key))
        assertEquals(listOf(LockAction.LOCK, LockAction.LOCK_AGAIN), state.actions(other))
    }

    @Test
    fun `with a pin and nothing locked there is nothing to unlock`() {
        assertEquals(listOf(LockAction.LOCK), QuickLockState(hasPin = true).actions(key))
    }

    @Test
    fun `the padlock in front of the name is not part of the key`() {
        val category = Category(id = 7, name = LockRepository.LOCK_MARK + "Sport", channelCount = 3, playlistId = 1, locked = true)
        assertEquals(key, QuickLockState().liveKey(category))
    }

    @Test
    fun `movie and series categories need an active playlist`() {
        assertNull(QuickLockState().vodKey(series = false, categoryId = "12"))
        assertEquals(LockRepository.vodKey(4, true, "12"), QuickLockState(playlistId = 4).vodKey(series = true, categoryId = "12"))
    }
}
