package com.bane23.iptvplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    @Test
    fun `javlja se kad vreme istekne i vraca se na iskljuceno`() = runTest {
        val timer = SleepTimer(backgroundScope)
        val events = mutableListOf<Unit>()
        backgroundScope.launch { timer.expired.toList(events) }
        runCurrent()

        timer.set(15)
        advanceTimeBy(14 * 60_000L)
        runCurrent()
        assertEquals(15, timer.minutes.value)
        assertEquals(0, events.size)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(0, timer.minutes.value)
        assertEquals(1, events.size)
    }

    @Test
    fun `novi izbor krece od pocetka a nula gasi tajmer`() = runTest {
        val timer = SleepTimer(backgroundScope)
        val events = mutableListOf<Unit>()
        backgroundScope.launch { timer.expired.toList(events) }
        runCurrent()

        timer.set(15)
        advanceTimeBy(10 * 60_000L)
        timer.set(30)
        advanceTimeBy(20 * 60_000L)
        runCurrent()
        assertEquals(30, timer.minutes.value)
        assertEquals(0, events.size)

        timer.set(0)
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(0, timer.minutes.value)
        assertEquals(0, events.size)
    }
}
