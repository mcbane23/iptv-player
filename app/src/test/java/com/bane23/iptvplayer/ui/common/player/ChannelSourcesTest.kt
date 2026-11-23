package com.bane23.iptvplayer.ui.common.player

import com.bane23.iptvplayer.data.repo.ChannelSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSourcesTest {

    @Test
    fun `same channel in merged playlists is told apart by playlist`() {
        val all = listOf(
            ChannelSource(1, 10, "Prva lista", "Sport Uno HD"),
            ChannelSource(2, 20, "Druga lista", "Sport Uno HD", height = 720),
        )
        assertEquals("Prva lista", sourceText(all[0], all))
        assertEquals("Druga lista · 720p", sourceText(all[1], all))
    }

    @Test
    fun `versions in one playlist are told apart by name and measured quality`() {
        val all = listOf(
            ChannelSource(1, 10, "Prva lista", "RS: Sport Uno 4K", height = 1080, bitrate = 6_600_000),
            ChannelSource(2, 10, "Prva lista", "RS: Sport Uno HD", height = 720, bitrate = 2_300_000),
            ChannelSource(3, 10, "Prva lista", "RS: Sport Uno RAW"),
        )
        assertEquals("RS: Sport Uno 4K · 1080p · 6.6 Mb/s", sourceText(all[0], all))
        assertEquals("RS: Sport Uno HD · 720p · 2.3 Mb/s", sourceText(all[1], all))
        assertEquals("RS: Sport Uno RAW", sourceText(all[2], all))
    }

    @Test
    fun `versions across playlists show name and playlist`() {
        val all = listOf(
            ChannelSource(1, 10, "Prva lista", "RS: Sport Uno 4K"),
            ChannelSource(2, 20, "Druga lista", "RS: Sport Uno HD", height = 720),
        )
        assertEquals("RS: Sport Uno HD · Druga lista · 720p", sourceText(all[1], all))
    }

    @Test
    fun `single source without a playlist name falls back to the channel name`() {
        val only = ChannelSource(1, 10, "", "Sport Uno")
        assertEquals("Sport Uno", sourceText(only, listOf(only)))
    }
}
