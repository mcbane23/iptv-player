package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamArchiveTest {
    private val hour = 3_600_000L
    private val now = 1_769_900_000_000L

    private fun listing(title: String, start: Long, stop: Long, startText: String, description: String = "") =
        """{"title":"$title","description":"$description","start":"$startText","start_timestamp":"${start / 1000}","stop_timestamp":${stop / 1000},"has_archive":1}"""

    @Test
    fun `reads finished programs newest first and decodes titles`() {
        val json = """{"epg_listings":[
            ${listing("RG5ldm5paw==", now - 5 * hour, now - 4 * hour, "2026-01-31 18:00:00", "VmVzdGkgZGFuYQ==")},
            ${listing("RmlsbQ==", now - 3 * hour, now - hour, "2026-01-31 20:00:00")},
            ${listing("VSB0b2t1", now - hour, now + hour, "2026-01-31 22:00:00")},
            ${listing("U3Rhcm8=", now - 100 * hour, now - 99 * hour, "2026-01-27 19:00:00")}
        ]}"""
        val entries = XtreamArchiveParser.parse(Json.parseToJsonElement(json), earliestMs = now - 48 * hour, nowMs = now)

        assertEquals(listOf("Film", "Dnevnik"), entries.map { it.title })
        assertEquals("2026-01-31:20-00", entries[0].startToken)
        assertEquals(120, entries[0].durationMinutes)
        assertNull(entries[0].description)
        assertEquals("Vesti dana", entries[1].description)
    }

    @Test
    fun `skips broken entries and tolerates other shapes`() {
        val json = """{"epg_listings":[
            {"title":"RmlsbQ==","start":"2026-01-31 20:00:00","start_timestamp":"x","stop_timestamp":"1"},
            {"title":"RmlsbQ==","start":"juče","start_timestamp":"${(now - 3 * hour) / 1000}","stop_timestamp":"${(now - hour) / 1000}"},
            {"title":"","start":"2026-01-31 20:00:00","start_timestamp":"${(now - 3 * hour) / 1000}","stop_timestamp":"${(now - hour) / 1000}"},
            "tekst"
        ]}"""
        assertTrue(XtreamArchiveParser.parse(Json.parseToJsonElement(json), 0, now).isEmpty())
        assertTrue(XtreamArchiveParser.parse(Json.parseToJsonElement("[]"), 0, now).isEmpty())
        assertTrue(XtreamArchiveParser.parse(Json.parseToJsonElement("""{"epg_listings":{}}"""), 0, now).isEmpty())
    }

    @Test
    fun `text that is not base64 is kept and utf8 is decoded`() {
        assertEquals("Obične vesti", XtreamArchiveParser.decode("Obične vesti"))
        assertEquals("Šta ima", XtreamArchiveParser.decode("xaB0YSBpbWE="))
        assertEquals("Film", XtreamArchiveParser.decode("RmlsbQ"))
        assertEquals("", XtreamArchiveParser.decode(null))
    }

    @Test
    fun `duration is rounded up to whole minutes`() {
        val entry = XtreamArchiveEntry(0, 61_000, "A", null, "2026-01-31:20-00")
        assertEquals(2, entry.durationMinutes)
    }

    @Test
    fun `builds the timeshift link`() {
        val url = XtreamUrls.timeshift(
            "http://server.invalid:8080/".toHttpUrl(), "izmisljen", "lozinka", 77, "2026-01-31:20-00", 120, "m3u8",
        )
        assertEquals("http://server.invalid:8080/timeshift/izmisljen/lozinka/120/2026-01-31:20-00/77.m3u8", url)
    }
}
