package com.bane23.iptvplayer.ui.common.vod

import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.Season
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeEpisodeTest {

    private val hour = 60 * 60 * 1000L

    /** Izmišljena serija: dve sezone po tri epizode, ID = sezona * 10 + broj. */
    private val details = SeriesDetails(
        name = "Izmisljena",
        poster = null,
        plot = null,
        genre = null,
        cast = null,
        releaseDate = null,
        rating = null,
        tmdbId = 9,
        seasons = (1..2).map { season ->
            Season(season, (1..3).map { number -> Episode(season * 10L + number, season, number, "E$number", "mkv", null, null, 3600) })
        },
    )

    private fun started(id: Long, at: Long) = WatchProgress(id, positionMs = hour / 3, durationMs = hour, updatedAt = at)

    private fun finished(id: Long, at: Long) = WatchProgress(id, positionMs = hour, durationMs = hour, watched = true, updatedAt = at)

    private fun resume(local: List<WatchProgress>, onTrakt: Map<Pair<Int, Int>, Long> = emptyMap()) =
        resumeEpisode(details, local, onTrakt)?.id

    @Test
    fun `nothing watched anywhere gives nothing to continue`() {
        assertNull(resume(emptyList()))
    }

    @Test
    fun `an episode started here is continued`() {
        assertEquals(12L, resume(listOf(started(12, at = 500), finished(11, at = 100))))
    }

    @Test
    fun `an episode finished here continues with the next one, also across seasons`() {
        assertEquals(21L, resume(listOf(finished(13, at = 500))))
    }

    @Test
    fun `an episode started here and later finished on another device continues with the next one`() {
        val local = listOf(started(22, at = 500), finished(21, at = 100))
        assertEquals(23L, resume(local, mapOf((2 to 1) to 150L, (2 to 2) to 900L)))
    }

    @Test
    fun `episodes watched further on another device move the continue point`() {
        val local = listOf(started(12, at = 500))
        assertEquals(22L, resume(local, mapOf((1 to 2) to 600L, (1 to 3) to 700L, (2 to 1) to 800L)))
    }

    @Test
    fun `watching again here after the account mark keeps the local episode`() {
        val local = listOf(started(11, at = 2_000))
        assertEquals(11L, resume(local, mapOf((1 to 1) to 100L, (1 to 2) to 200L)))
    }

    @Test
    fun `nothing watched here continues after the latest account episode`() {
        assertEquals(13L, resume(emptyList(), mapOf((1 to 1) to 100L, (1 to 2) to 300L)))
    }

    @Test
    fun `a season marked at once continues after its last episode`() {
        val marked = mapOf((1 to 1) to 400L, (1 to 2) to 400L, (1 to 3) to 400L)
        assertEquals(21L, resume(emptyList(), marked))
    }

    @Test
    fun `a position from another device that is newer than the account marks is continued`() {
        val local = listOf(started(23, at = 1_000))
        assertEquals(23L, resume(local, mapOf((2 to 1) to 800L, (2 to 2) to 900L)))
    }

    @Test
    fun `started here and watched on the account at an unknown time counts as finished elsewhere`() {
        assertEquals(13L, resume(listOf(started(12, at = 500)), mapOf((1 to 2) to 0L)))
    }

    @Test
    fun `the last episode finished leaves nothing to continue`() {
        assertNull(resume(listOf(finished(23, at = 500))))
    }
}
