package com.bane23.iptvplayer.ui.common.vod

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseTitleTest {

    @Test
    fun episodeRepeatingSeriesNameKeepsOnlySeries() {
        assertEquals(
            "Tiha luka (2021) (RS)",
            pauseTitle("EN - Tiha luka (2021) (RS)", "EN - Tiha luka (2021) (RS) - S03E04"),
        )
    }

    @Test
    fun realEpisodeNameStays() {
        assertEquals("Tiha luka · Svetionik", pauseTitle("EX-YU | Tiha luka", "Tiha luka S01E02 - Svetionik"))
        assertEquals("Tiha luka · Svetionik", pauseTitle("Tiha luka", "Svetionik"))
    }

    @Test
    fun episodeNumberOnlyIsDropped() {
        assertEquals("Tiha luka", pauseTitle("Tiha luka", "4"))
    }

    @Test
    fun movieLosesListPrefix() {
        assertEquals("Poslednji voz (2019)", pauseTitle("EN - Poslednji voz (2019)", null))
        assertEquals("Poslednji voz", pauseTitle("Poslednji voz", null))
    }
}
