package com.bane23.iptvplayer.data.trakt

import com.bane23.iptvplayer.data.db.TraktPendingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class TraktPayloadsTest {

    @Test
    fun `history groups episodes by show and season`() {
        val body = TraktPayloads.history(
            listOf(
                TraktKey.movie(100),
                TraktKey.episode(7, season = 2, episode = 3),
                TraktKey.episode(7, season = 1, episode = 2),
                TraktKey.episode(7, season = 1, episode = 1),
                TraktKey.episode(8, season = 1, episode = 1),
                TraktKey.movie(100),
            ),
            watchedAt = "2026-01-01T00:00:00.000Z",
        )
        assertEquals(listOf(100L), body.movies.map { it.ids.tmdb })
        assertEquals("2026-01-01T00:00:00.000Z", body.movies.single().watchedAt)
        val show = body.shows.first { it.ids.tmdb == 7L }
        assertEquals(listOf(1, 2), show.seasons.map { it.number })
        assertEquals(listOf(1, 2), show.seasons.first().episodes.map { it.number })
        assertEquals(2, body.shows.size)
    }

    @Test
    fun `remove body has no watched time`() {
        val body = TraktPayloads.history(listOf(TraktKey.movie(1)), watchedAt = null)
        assertNull(body.movies.single().watchedAt)
        val json = TraktJson.encodeToString(HistoryBody.serializer(), body)
        assertEquals("""{"movies":[{"ids":{"tmdb":1}}],"shows":[]}""", json)
    }

    @Test
    fun `watched lists become rows and items without tmdb are skipped`() {
        val movies = TraktJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(WatchedMovie.serializer()),
            """[{"plays":1,"last_watched_at":"2026-01-01T00:00:00.000Z","movie":{"title":"Izmisljen","ids":{"trakt":1,"tmdb":55}}},
               {"plays":1,"movie":{"title":"Bez tmdb","ids":{"trakt":2}}}]""",
        )
        val shows = TraktJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(WatchedShow.serializer()),
            """[{"plays":2,"show":{"title":"Izmisljena","ids":{"tmdb":9}},
                 "seasons":[{"number":1,"episodes":[{"number":1,"plays":1},{"number":2,"plays":1}]}]}]""",
        )
        val rows = TraktPayloads.watchedRows(movies, shows, now = 5)
        assertEquals(3, rows.size)
        assertEquals(listOf(55L), rows.filter { it.kind == TraktKey.MOVIE }.map { it.tmdbId })
        assertEquals(listOf(1 to 1, 1 to 2), rows.filter { it.kind == TraktKey.EPISODE }.map { it.season to it.episode })
    }

    @Test
    fun `only the latest change of an item is sent`() {
        val pending = listOf(
            TraktPendingEntity(1, 1, "movie", 10, 0, watched = true, createdAt = 0),
            TraktPendingEntity(2, 1, "episode", 20, 5, watched = true, createdAt = 0),
            TraktPendingEntity(3, 1, "movie", 10, 0, watched = false, createdAt = 0),
        )
        val latest = TraktPayloads.latestChanges(pending)
        assertEquals(listOf(2L, 3L), latest.map { it.id })
        assertEquals(false, latest.last().watched)
    }

    @Test
    fun `time is sent in utc`() {
        assertEquals("1970-01-01T00:00:01.000Z", TraktPayloads.isoTime(1000))
    }
}
