package com.bane23.iptvplayer.data.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupTest {
    // 2026-01-31 20:05:09 UTC, traje jedan sat.
    private val start = 1_769_889_909_000L
    private val stop = start + 3_600_000L
    private val now = stop + 7_200_000L
    private val live = "http://tv.invalid/kanal/index.m3u8?token=abc"

    @Test
    fun `default type fills the template`() {
        val url = Catchup.url(
            "default", "http://tv.invalid/arh?s=\${start}&e={utcend}&d=\${duration}&n={lutc}&o=\${offset}", live, start, stop, now,
        )
        assertEquals(
            "http://tv.invalid/arh?s=1769889909&e=1769893509&d=3600&n=1769900709&o=10800",
            url,
        )
    }

    @Test
    fun `date parts are written in utc with two digits`() {
        val url = Catchup.url("default", "http://tv.invalid/{Y}/{m}/{d}/{H}-{M}-{S}.ts", null, start, stop, now)
        assertEquals("http://tv.invalid/2026/01/31/20-05-09.ts", url)
    }

    @Test
    fun `unknown placeholders stay as they are`() {
        assertEquals(
            "http://tv.invalid/{nepoznato}/1769889909",
            Catchup.url("default", "http://tv.invalid/{nepoznato}/{utc}", null, start, stop, now),
        )
    }

    @Test
    fun `a template without a type is treated as default`() {
        assertTrue(Catchup.isSupported(null, "http://tv.invalid/{utc}", null))
        assertEquals("http://tv.invalid/1769889909", Catchup.url(null, "http://tv.invalid/{utc}", null, start, stop, now))
    }

    @Test
    fun `append adds the template to the channel address`() {
        assertEquals(
            "$live&utc=1769889909&dur=3600",
            Catchup.url("append", "&utc={utc}&dur={duration}", live, start, stop, now),
        )
    }

    @Test
    fun `shift adds utc and lutc with the right separator`() {
        assertEquals("$live&utc=1769889909&lutc=1769900709", Catchup.url("shift", null, live, start, stop, now))
        assertEquals(
            "http://tv.invalid/kanal.ts?utc=1769889909&lutc=1769900709",
            Catchup.url("shift", null, "http://tv.invalid/kanal.ts", start, stop, now),
        )
    }

    @Test
    fun `flussonic rewrites the last path segment and keeps the query`() {
        assertEquals(
            "http://tv.invalid/kanal/index-1769889909-3600.m3u8?token=abc",
            Catchup.url("flussonic", null, live, start, stop, now),
        )
        assertEquals(
            "http://tv.invalid/kanal/timeshift_abs-1769889909.ts",
            Catchup.url("fs", null, "http://tv.invalid/kanal/mpegts", start, stop, now),
        )
        assertNull(Catchup.url("flussonic", null, "http://tv.invalid/kanal.ts", start, stop, now))
        assertFalse(Catchup.isSupported("flussonic", null, "http://tv.invalid/kanal.ts"))
    }

    @Test
    fun `unsupported or incomplete attributes give no archive`() {
        assertFalse(Catchup.isSupported(null, null, live))
        assertFalse(Catchup.isSupported("default", null, live))
        assertFalse(Catchup.isSupported("append", "&utc={utc}", null))
        assertFalse(Catchup.isSupported("nesto-novo", "http://tv.invalid/{utc}", live))
        assertNull(Catchup.url("shift", null, live, stop, start, now))
    }
}
