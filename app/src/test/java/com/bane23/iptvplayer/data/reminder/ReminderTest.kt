package com.bane23.iptvplayer.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {

    private val reminder = Reminder(
        playlistId = 3,
        channelKey = "kanal|7",
        startMs = 1_000_000,
        channelName = "Izmišljeni kanal | HD",
        title = "Emisija: prvi deo | repriza",
    )

    @Test
    fun `encode and decode keep every field`() = assertEquals(reminder, Reminder.decode(reminder.encode()))

    @Test
    fun `broken text is ignored`() {
        assertNull(Reminder.decode(""))
        assertNull(Reminder.decode("samo tekst"))
        assertNull(Reminder.decode(reminder.encode().replaceFirst("3", "x")))
    }

    @Test
    fun `due from the start until the keep window ends`() {
        assertFalse(reminder.isDue(reminder.startMs - 1))
        assertTrue(reminder.isDue(reminder.startMs))
        assertTrue(reminder.isDue(reminder.startMs + Reminder.KEEP_AFTER_START_MS - 1))
        assertFalse(reminder.isDue(reminder.startMs + Reminder.KEEP_AFTER_START_MS))
        assertTrue(reminder.isExpired(reminder.startMs + Reminder.KEEP_AFTER_START_MS))
        assertFalse(reminder.isExpired(reminder.startMs))
    }

    @Test
    fun `key matches the guide programme key`() = assertEquals("3|kanal|7|1000000", reminder.key)
}
