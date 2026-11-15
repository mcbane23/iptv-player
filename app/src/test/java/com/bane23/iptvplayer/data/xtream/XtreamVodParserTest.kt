package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class XtreamVodParserTest {

    private fun json(text: String): JsonElement = XtreamJson.parseToJsonElement(text)

    @Test
    fun `vod streams accept mixed types`() {
        val list = XtreamJson.decodeFromString(
            ListSerializer(XtreamVodStream.serializer()),
            """
            [{"num":1,"name":"Izmisljeni film","stream_type":"movie","stream_id":501,
              "stream_icon":"http://slike.invalid/501.jpg","rating":7.4,"container_extension":"mkv"},
             {"name":"Drugi film","stream_id":"502","stream_icon":null,"rating":"","container_extension":"mp4"}]
            """.trimIndent(),
        )
        assertEquals("501", list[0].streamId)
        assertEquals("7.4", list[0].rating)
        assertEquals("mkv", list[0].extension)
        assertEquals("502", list[1].streamId)
        assertNull(list[1].icon)
    }

    @Test
    fun `series list is read`() {
        val list = XtreamJson.decodeFromString(
            ListSerializer(XtreamSeries.serializer()),
            """[{"num":1,"name":"Izmisljena serija","series_id":77,"cover":"http://slike.invalid/77.jpg","rating":"8"}]""",
        )
        assertEquals("77", list.single().seriesId)
        assertEquals("Izmisljena serija", list.single().name)
    }

    @Test
    fun `movie details are read`() {
        val movie = XtreamVodParser.movie(
            json(
                """
                {"info":{"movie_image":"http://slike.invalid/501.jpg","plot":" Kratak opis. ","genre":"Drama",
                         "cast":"Glumac Jedan, Glumac Dva","director":"Neko","releasedate":"2020-05-01",
                         "rating":"7.4","duration_secs":5400,"duration":"01:30:00"},
                 "movie_data":{"stream_id":501,"name":"Izmisljeni film","container_extension":"mkv"}}
                """.trimIndent(),
            ),
        )
        assertEquals("Izmisljeni film", movie.name)
        assertEquals("Kratak opis.", movie.plot)
        assertEquals("2020-05-01", movie.releaseDate)
        assertEquals(5400L, movie.durationSeconds)
        assertEquals("mkv", movie.extension)
    }

    @Test
    fun `movie details survive empty or odd responses`() {
        val empty = XtreamVodParser.movie(json("""{"info":[],"movie_data":{"stream_id":501,"container_extension":"mp4"}}"""))
        assertNull(empty.plot)
        assertNull(empty.durationSeconds)
        assertEquals("mp4", empty.extension)

        val nothing = XtreamVodParser.movie(json("[]"))
        assertNull(nothing.name)
        assertNull(nothing.extension)

        val odd = XtreamVodParser.movie(json("""{"info":{"plot":"","duration_secs":"0","genre":null,"cast":["a"]}}"""))
        assertNull(odd.plot)
        assertNull(odd.durationSeconds)
        assertNull(odd.genre)
        assertNull(odd.cast)
    }

    @Test
    fun `series with episodes as map of seasons`() {
        val series = XtreamVodParser.series(
            json(
                """
                {"seasons":[],"info":{"name":"Izmisljena serija","cover":"http://slike.invalid/77.jpg","plot":"Opis."},
                 "episodes":{
                   "2":[{"id":"903","episode_num":1,"title":"S02E01","container_extension":"mkv","info":[]}],
                   "1":[{"id":"902","episode_num":"2","title":"Druga","container_extension":"mp4",
                         "info":{"duration_secs":1500,"plot":"O drugoj.","movie_image":"http://slike.invalid/902.jpg"}},
                        {"id":901,"episode_num":1,"title":"Prva","container_extension":"mp4","info":{}}]}}
                """.trimIndent(),
            ),
        )
        assertEquals("Izmisljena serija", series.name)
        assertEquals(listOf(1, 2), series.seasons.map { it.number })
        assertEquals(listOf(901L, 902L, 903L), series.episodes.map { it.id })
        val second = series.seasons[0].episodes[1]
        assertEquals("Druga", second.title)
        assertEquals(1500L, second.durationSeconds)
        assertEquals("O drugoj.", second.plot)
        assertEquals("mkv", series.seasons[1].episodes[0].extension)
    }

    @Test
    fun `series with episodes as list of lists`() {
        val series = XtreamVodParser.series(
            json(
                """
                {"info":[],"episodes":[
                  [{"id":"11","title":"Prva"},{"id":"12","title":"Druga"},{"title":"Bez ID-ja"}],
                  [{"id":"21","episode_num":"1"}]]}
                """.trimIndent(),
            ),
        )
        assertNull(series.name)
        assertEquals(listOf(1, 2), series.seasons.map { it.number })
        assertEquals(listOf(1, 2), series.seasons[0].episodes.map { it.number })
        assertEquals("mp4", series.seasons[0].episodes[0].extension)
        assertEquals("1", series.seasons[1].episodes[0].title)
    }

    @Test
    fun `series without episodes`() {
        assertTrue(XtreamVodParser.series(json("""{"info":{"name":"Prazna"},"episodes":[]}""")).seasons.isEmpty())
        assertTrue(XtreamVodParser.series(json("""{"info":{"name":"Prazna"}}""")).seasons.isEmpty())
        assertTrue(XtreamVodParser.series(json("[]")).seasons.isEmpty())
    }

    @Test
    fun `next episode crosses seasons`() {
        val series = XtreamVodParser.series(
            json("""{"episodes":{"1":[{"id":1,"episode_num":1},{"id":2,"episode_num":2}],"2":[{"id":3,"episode_num":1}]}}"""),
        )
        assertEquals(2L, series.episodeAfter(1)?.id)
        assertEquals(3L, series.episodeAfter(2)?.id)
        assertNull(series.episodeAfter(3))
        assertNull(series.episodeAfter(99))
    }

    @Test
    fun `stream urls`() {
        val server = XtreamUrls.normalizeServer("tv.invalid:8080")!!
        assertEquals(
            "http://tv.invalid:8080/movie/proba/tajna/501.mkv",
            XtreamUrls.movieStream(server, "proba", "tajna", 501, "mkv"),
        )
        assertEquals(
            "http://tv.invalid:8080/series/proba/tajna/902.mp4",
            XtreamUrls.episodeStream(server, "proba", "tajna", 902, "mp4"),
        )
        assertEquals(
            "http://tv.invalid:8080/player_api.php?username=proba&password=tajna&action=get_vod_info&vod_id=501",
            XtreamUrls.playerApi(server, "proba", "tajna", XtreamUrls.ACTION_VOD_INFO, "vod_id", "501"),
        )
    }
}
