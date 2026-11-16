package com.bane23.iptvplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastUrlsTest {
    @Test
    fun `hls address wins over raw ts for a channel`() {
        val urls = listOf("http://example.invalid/live/u/p/12.ts", "http://example.invalid/live/u/p/12.m3u8")
        assertEquals("http://example.invalid/live/u/p/12.m3u8", CastUrls.pick(urls))
    }

    @Test
    fun `mp4 wins over other movie formats and first is the fallback`() {
        assertEquals(
            "http://example.invalid/movie/7.mp4",
            CastUrls.pick(listOf("http://example.invalid/movie/7.mkv", "http://example.invalid/movie/7.mp4")),
        )
        assertEquals("http://example.invalid/movie/7.mkv", CastUrls.pick(listOf("http://example.invalid/movie/7.mkv")))
    }

    @Test
    fun `addresses that are not http are skipped`() {
        assertNull(CastUrls.pick(listOf("file:///sdcard/list.m3u8", "rtmp://example.invalid/x")))
        assertNull(CastUrls.pick(emptyList()))
    }

    @Test
    fun `extension ignores query and fragment`() {
        assertEquals("m3u8", CastUrls.extension("http://example.invalid/a/b.M3U8?token=x.y#t"))
        assertEquals("", CastUrls.extension("http://example.invalid/stream/12?fmt=a.b"))
        assertEquals("", CastUrls.extension("http://example.invalid:8080/play"))
    }

    @Test
    fun `content type follows the extension`() {
        assertEquals(CastUrls.HLS, CastUrls.contentType("http://example.invalid/1.m3u8", live = true))
        assertEquals(CastUrls.MP4, CastUrls.contentType("http://example.invalid/1.mp4?x=1", live = false))
        assertEquals("video/x-matroska", CastUrls.contentType("http://example.invalid/1.mkv", live = false))
        // Bez nastavka: kanal se šalje kao HLS, film kao MP4.
        assertEquals(CastUrls.HLS, CastUrls.contentType("http://example.invalid/play/1", live = true))
        assertEquals(CastUrls.MP4, CastUrls.contentType("http://example.invalid/play/1", live = false))
    }
}
