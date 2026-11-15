package com.bane23.iptvplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelTest {

    private fun channel(start: Long?, stop: Long?) = Channel(
        id = 1,
        playlistId = 1,
        categoryId = null,
        name = "Test kanal",
        logo = null,
        number = null,
        sortOrder = 0,
        nowTitle = "Emisija",
        nowStartMs = start,
        nowStopMs = stop,
    )

    @Test
    fun progressIsShareOfProgramThatPassed() {
        assertEquals(0.25f, channel(1_000, 5_000).nowProgress(2_000)!!, 0.0001f)
    }

    @Test
    fun progressStaysBetweenZeroAndOne() {
        assertEquals(0f, channel(1_000, 5_000).nowProgress(500)!!, 0f)
        assertEquals(1f, channel(1_000, 5_000).nowProgress(9_000)!!, 0f)
    }

    @Test
    fun noProgressWithoutGuideOrWithBrokenTimes() {
        assertNull(channel(null, null).nowProgress(2_000))
        assertNull(channel(5_000, 5_000).nowProgress(5_000))
    }
}
