package com.bane23.iptvplayer.data.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class SubtitleSearchTest {

    @Test
    fun `clean title drops list prefix, brackets, quality and takes the year`() {
        val clean = SubtitleSearch.cleanTitle("EN - Izmisljeni Film (2020) [4K]")
        assertEquals("Izmisljeni Film", clean.title)
        assertEquals(2020, clean.year)
    }

    @Test
    fun `clean title keeps a plain name and has no year`() {
        val clean = SubtitleSearch.cleanTitle("Neka Serija")
        assertEquals("Neka Serija", clean.title)
        assertNull(clean.year)
    }

    @Test
    fun `clean title keeps a name that is itself a year`() {
        val clean = SubtitleSearch.cleanTitle("1999 (2015)")
        assertEquals("1999", clean.title)
        assertEquals(2015, clean.year)
        assertEquals("2031", SubtitleSearch.cleanTitle("2031").title)
    }

    @Test
    fun `clean title drops episode mark and what follows`() {
        assertEquals("Neka Serija", SubtitleSearch.cleanTitle("EX-YU | Neka Serija S02 E05 HD").title)
    }

    @Test
    fun `clean title falls back to the raw name when nothing is left`() {
        assertEquals("[HD]", SubtitleSearch.cleanTitle(" [HD] ").title)
    }

    @Test
    fun `languages put the preferred first and always end with english`() {
        assertEquals(listOf("de", "fr", "en"), SubtitleSearch.languages(preferred = listOf("de"), device = "fr-FR"))
        assertEquals(listOf("en"), SubtitleSearch.languages(preferred = emptyList(), device = "en"))
    }

    @Test
    fun `serbian brings croatian and bosnian along`() {
        assertEquals(listOf("sr", "hr", "bs", "en"), SubtitleSearch.languages(preferred = emptyList(), device = "sr"))
        assertEquals(listOf("hr", "sr", "bs", "en"), SubtitleSearch.languages(preferred = listOf("hr"), device = "en"))
    }

    @Test
    fun `several preferred languages keep their order ahead of the extras`() {
        assertEquals(
            listOf("sr", "en", "de", "hr", "bs"),
            SubtitleSearch.languages(preferred = listOf("sr", "en", "de"), device = ""),
        )
        assertEquals(
            listOf("de", "hr", "sr", "bs", "fr", "en"),
            SubtitleSearch.languages(preferred = listOf("de", " HR ", ""), device = "fr-FR"),
        )
    }

    @Test
    fun `results are parsed and ordered by language then downloads`() {
        val json = """
            {"total_count": 4, "data": [
              {"id": "1", "type": "subtitle", "attributes": {"language": "en", "download_count": 900,
                "release": "Izmisljeno.Izdanje.A", "files": [{"file_id": 11, "file_name": "a.srt"}]}},
              {"id": "2", "attributes": {"language": "sr", "download_count": 5, "hearing_impaired": true,
                "release": "", "files": [{"file_id": 12, "file_name": "b.srt"}]}},
              {"id": "3", "attributes": {"language": "sr", "download_count": 50, "ai_translated": true,
                "release": "Izmisljeno.Izdanje.C", "files": [{"file_id": 13}]}},
              {"id": "4", "attributes": {"language": "hr", "download_count": 70, "files": []}},
              {"id": "5", "attributes": {"language": "SR", "download_count": 20,
                "release": "Izmisljeno.Izdanje.E", "files": [{"file_id": 15}]}}
            ]}
        """.trimIndent()
        val response = OpenSubtitlesJson.decodeFromString(OsSearchResponse.serializer(), json)
        val results = SubtitleSearch.results(response, listOf("sr", "hr", "bs", "en"))

        // Titl bez fajla se preskače; mašinski prevod ide iza ostalih na istom jeziku.
        assertEquals(listOf(15L, 12L, 13L, 11L), results.map { it.fileId })
        assertEquals("b.srt", results[1].name)
        assertTrue(results[1].hearingImpaired)
        assertTrue(results[2].machineTranslated)
        assertFalse(results[0].machineTranslated)
        assertEquals("sr", results[0].language)
    }

    @Test
    fun `download request carries the file id and format`() {
        val json = OpenSubtitlesJson.encodeToString(OsDownloadRequest.serializer(), OsDownloadRequest(42))
        assertEquals("""{"file_id":42,"sub_format":"srt"}""", json)
    }

    @Test
    fun `saved choice is kept per playlist and kind`() {
        val movie = SubtitleSearch.savedKey(playlistId = 3, itemId = 7, episode = false)
        val episode = SubtitleSearch.savedKey(playlistId = 3, itemId = 7, episode = true)
        val other = SubtitleSearch.savedKey(playlistId = 4, itemId = 7, episode = false)
        assertEquals(3, setOf(movie, episode, other).size)

        val index = SavedSubtitles()
            .with(movie, SavedSubtitle(11, "sr", "Izmisljeno.Izdanje.A"))
            .with(episode, SavedSubtitle(12, "en", "Izmisljeno.Izdanje.B"))
            .with(movie, SavedSubtitle(13, "hr", "Izmisljeno.Izdanje.C"))
        assertEquals(13L, index.items[movie]?.fileId)
        assertEquals(2, index.items.size)
        assertNull(index.without(movie).items[movie])
        assertEquals(index, index.without(other))
    }

    @Test
    fun `saved choices survive json and drop when the file is gone`() {
        val key = SubtitleSearch.savedKey(1, 2, episode = true)
        val index = SavedSubtitles().with(key, SavedSubtitle(21, "sr", "Izmisljeno")).with("1/m/9", SavedSubtitle(22, "", "x"))
        val json = OpenSubtitlesJson.encodeToString(SavedSubtitles.serializer(), index)
        val back = OpenSubtitlesJson.decodeFromString(SavedSubtitles.serializer(), json)
        assertEquals(index, back)
        assertEquals(setOf(key), back.onlyWith(setOf(21L)).items.keys)
    }
}
