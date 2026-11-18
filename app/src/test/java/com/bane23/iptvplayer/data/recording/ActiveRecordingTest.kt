package com.bane23.iptvplayer.data.recording

import com.bane23.iptvplayer.domain.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRecordingTest {

    private fun channel(id: Long, playlistId: Long, stableKey: String) = Channel(
        id = id,
        playlistId = playlistId,
        categoryId = null,
        name = "Test kanal",
        logo = null,
        number = null,
        sortOrder = 0,
        stableKey = stableKey,
    )

    private val recording = ActiveRecording(id = 7, playlistId = 1, channelKey = "xtream:42", channelName = "Test kanal")

    @Test
    fun sameChannelIsRecognizedAfterPlaylistRefreshChangesItsId() {
        assertTrue(recording.isOf(channel(id = 10, playlistId = 1, stableKey = "xtream:42")))
        assertTrue(recording.isOf(channel(id = 9_999, playlistId = 1, stableKey = "xtream:42")))
    }

    @Test
    fun otherChannelOrOtherPlaylistIsNotTheRecordedOne() {
        assertFalse(recording.isOf(channel(id = 10, playlistId = 1, stableKey = "xtream:43")))
        assertFalse(recording.isOf(channel(id = 10, playlistId = 2, stableKey = "xtream:42")))
    }

    @Test
    fun keyMatchesTheKeyOfItsChannel() {
        assertEquals(recording.key, ActiveRecording.channelKeyOf(channel(id = 10, playlistId = 1, stableKey = "xtream:42")))
    }
}
