package com.bane23.iptvplayer.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class XtreamUrlsTest {

    @Test
    fun `normalize adds scheme and strips path and query`() {
        assertEquals("http://tv.invalid:8080/", XtreamUrls.normalizeServer("tv.invalid:8080").toString())
        assertEquals("https://tv.invalid/", XtreamUrls.normalizeServer(" https://tv.invalid/ ").toString())
        assertEquals(
            "http://tv.invalid:8080/",
            XtreamUrls.normalizeServer("http://tv.invalid:8080/player_api.php?username=a&password=b").toString(),
        )
    }

    @Test
    fun `normalize rejects invalid input`() {
        assertNull(XtreamUrls.normalizeServer(""))
        assertNull(XtreamUrls.normalizeServer("   "))
        assertNull(XtreamUrls.normalizeServer("ftp://tv.invalid"))
        assertNull(XtreamUrls.normalizeServer("http://"))
    }

    @Test
    fun `player api url with and without action`() {
        val server = XtreamUrls.normalizeServer("tv.invalid:8080")!!
        assertEquals(
            "http://tv.invalid:8080/player_api.php?username=proba&password=tajna",
            XtreamUrls.playerApi(server, "proba", "tajna"),
        )
        assertEquals(
            "http://tv.invalid:8080/player_api.php?username=proba&password=tajna&action=get_live_streams",
            XtreamUrls.playerApi(server, "proba", "tajna", XtreamUrls.ACTION_LIVE_STREAMS),
        )
    }

    @Test
    fun `credentials with special characters are encoded`() {
        val server = XtreamUrls.normalizeServer("tv.invalid")!!
        assertEquals(
            "http://tv.invalid/player_api.php?username=a%20b&password=x%26y%3Dz",
            XtreamUrls.playerApi(server, "a b", "x&y=z"),
        )
    }

    @Test
    fun `live stream url`() {
        val server = XtreamUrls.normalizeServer("tv.invalid:8080")!!
        assertEquals(
            "http://tv.invalid:8080/live/proba/tajna/101.ts",
            XtreamUrls.liveStream(server, "proba", "tajna", 101),
        )
        assertEquals(
            "http://tv.invalid:8080/live/proba/tajna/101.m3u8",
            XtreamUrls.liveStream(server, "proba", "tajna", 101, "m3u8"),
        )
    }
}
