package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class ReadVodItemsTest {
    private fun read(kind: VodKind, json: String) = readVodItems(kind, json.byteInputStream())

    @Test
    fun `movies are read with id, name, poster, rating, extension and tmdb id`() {
        val items = read(
            VodKind.MOVIES,
            """[
                {"stream_id":11,"name":"Izmišljeni film","stream_icon":"poster-a","rating":"7.46","container_extension":"mkv","tmdb":"501","extra":{"x":1}},
                {"stream_id":"12","name":"","stream_icon":"","rating":0,"tmdb_id":502},
                {"name":"Bez ID-ja"}
            ]""",
        )
        assertEquals(
            listOf(
                VodItem(id = 11, name = "Izmišljeni film", poster = "poster-a", rating = "7.4", extension = "mkv", tmdbId = 501),
                VodItem(id = 12, name = "12", poster = null, rating = null, extension = null, tmdbId = 502),
            ),
            items,
        )
    }

    @Test
    fun `series are read with cover and tmdb id`() {
        val items = read(
            VodKind.SERIES,
            """[{"series_id":21,"name":"Izmišljena serija","cover":"poster-b","rating":"8","tmdb":"601"},{"series_id":22,"name":"Druga"}]""",
        )
        assertEquals(listOf(21L, 22L), items.map { it.id })
        assertEquals("poster-b", items[0].poster)
        assertEquals(601L, items[0].tmdbId)
        assertNull(items[0].extension)
        assertNull(items[1].tmdbId)
    }

    @Test
    fun `empty list gives no items`() {
        assertTrue(read(VodKind.MOVIES, "[]").isEmpty())
        assertTrue(read(VodKind.SERIES, " [ ] ").isEmpty())
    }
}
