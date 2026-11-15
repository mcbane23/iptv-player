package com.bane23.iptvplayer.data.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Svi podaci u testovima su izmišljeni (domen `.invalid` ne postoji). */
class M3uParserTest {

    private fun parse(text: String): List<M3uItem> =
        M3uParser.parse(text.lineSequence()).toList()

    private fun entries(text: String): List<M3uItem.Entry> =
        parse(text).filterIsInstance<M3uItem.Entry>()

    @Test
    fun `parses header with epg url`() {
        val items = parse("#EXTM3U url-tvg=\"http://epg.test.invalid/guide.xml\"\n")
        assertEquals(listOf(M3uItem.Header("http://epg.test.invalid/guide.xml")), items)
    }

    @Test
    fun `parses x-tvg-url and takes first of several`() {
        val items = parse("#EXTM3U x-tvg-url=\"http://a.invalid/1.xml,http://b.invalid/2.xml\"")
        assertEquals(M3uItem.Header("http://a.invalid/1.xml"), items.single())
    }

    @Test
    fun `header with BOM and no attributes`() {
        val items = parse("﻿#EXTM3U\n")
        assertEquals(M3uItem.Header(null), items.single())
    }

    @Test
    fun `parses entry with all attributes`() {
        val entry = entries(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="prvi.test" tvg-name="Prvi Test" tvg-logo="http://img.invalid/prvi.png" group-title="Vesti" tvg-chno="7" catchup="default" catchup-source="http://tv.invalid/arhiva/{utc}" catchup-days="3",Prvi Test HD
            http://tv.invalid/live/1.m3u8
            """.trimIndent(),
        ).single()

        assertEquals("Prvi Test HD", entry.name)
        assertEquals("http://tv.invalid/live/1.m3u8", entry.url)
        assertEquals("prvi.test", entry.tvgId)
        assertEquals("Prvi Test", entry.tvgName)
        assertEquals("http://img.invalid/prvi.png", entry.logo)
        assertEquals("Vesti", entry.group)
        assertEquals(7, entry.number)
        assertEquals("default", entry.catchupType)
        assertEquals("http://tv.invalid/arhiva/{utc}", entry.catchupSource)
        assertEquals(3, entry.catchupDays)
    }

    @Test
    fun `comma inside quoted attribute does not split the name`() {
        val entry = entries(
            """
            #EXTINF:-1 tvg-name="Film, drugi deo" group-title="Filmovi, Serije",Kanal, sa zarezom
            http://tv.invalid/2
            """.trimIndent(),
        ).single()
        assertEquals("Kanal, sa zarezom", entry.name)
        assertEquals("Film, drugi deo", entry.tvgName)
        assertEquals("Filmovi, Serije", entry.group)
    }

    @Test
    fun `entry without attributes`() {
        val entry = entries("#EXTINF:-1,Običan kanal\nhttp://tv.invalid/3").single()
        assertEquals("Običan kanal", entry.name)
        assertNull(entry.group)
        assertNull(entry.tvgId)
    }

    @Test
    fun `unquoted attribute values`() {
        val entry = entries("#EXTINF:0 tvg-id=abc group-title=Sport,Kanal\nhttp://tv.invalid/4").single()
        assertEquals("abc", entry.tvgId)
        assertEquals("Sport", entry.group)
        assertEquals("Kanal", entry.name)
    }

    @Test
    fun `EXTGRP is used when group-title is missing`() {
        val entry = entries(
            """
            #EXTINF:-1,Kanal
            #EXTGRP:Muzika
            http://tv.invalid/5
            """.trimIndent(),
        ).single()
        assertEquals("Muzika", entry.group)
    }

    @Test
    fun `group-title wins over EXTGRP`() {
        val entry = entries(
            """
            #EXTINF:-1 group-title="Deca",Kanal
            #EXTGRP:Muzika
            http://tv.invalid/6
            """.trimIndent(),
        ).single()
        assertEquals("Deca", entry.group)
    }

    @Test
    fun `EXTVLCOPT user agent and referrer`() {
        val entry = entries(
            """
            #EXTINF:-1,Kanal
            #EXTVLCOPT:http-user-agent=TestAgent/1.0 (probni)
            #EXTVLCOPT:http-referrer=http://ref.invalid/
            http://tv.invalid/7
            """.trimIndent(),
        ).single()
        assertEquals("TestAgent/1.0 (probni)", entry.userAgent)
        assertEquals("http://ref.invalid/", entry.referrer)
    }

    @Test
    fun `malformed lines are tolerated`() {
        val result = entries(
            """
            #EXTM3U
            ovo nije deo liste
            #EXTINF:-1 tvg-id="nezatvoren,Kanal bez zatvorenog navodnika
            http://tv.invalid/8
            #EXTINF:
            http://tv.invalid/9
            #EXTINF:-1 group-title="Dupli",Prvi red
            #EXTINF:-1 group-title="Dupli",Drugi red
            http://tv.invalid/10
            #NEPOZNATO:nešto

            #EXTINF:-1,Bez linka
            """.trimIndent(),
        )
        assertEquals(3, result.size)
        assertEquals("http://tv.invalid/8", result[0].url)
        // prazan #EXTINF: naziv se uzima iz linka
        assertEquals("http://tv.invalid/9", result[1].name)
        assertEquals("Drugi red", result[2].name)
    }

    @Test
    fun `list without header still yields entries`() {
        val items = parse("#EXTINF:-1,Kanal\nhttp://tv.invalid/11")
        assertEquals(1, items.size)
        assertTrue(items.single() is M3uItem.Entry)
    }

    @Test
    fun `windows line endings and surrounding whitespace`() {
        val entry = entries("#EXTM3U\r\n  #EXTINF:-1 tvg-id=\"x\" , Kanal \r\n  http://tv.invalid/12  \r\n").single()
        assertEquals("Kanal", entry.name)
        assertEquals("http://tv.invalid/12", entry.url)
        assertEquals("x", entry.tvgId)
    }

    @Test
    fun `parsing is lazy and handles large input`() {
        val lines = sequence {
            yield("#EXTM3U")
            repeat(50_000) { i ->
                yield("#EXTINF:-1 tvg-id=\"k$i\" group-title=\"Grupa ${i % 20}\",Kanal $i")
                yield("http://tv.invalid/live/$i")
            }
        }
        val count = M3uParser.parse(lines).count { it is M3uItem.Entry }
        assertEquals(50_000, count)
    }

    @Test
    fun `empty tvg-id is treated as missing`() {
        val entry = entries("#EXTINF:-1 tvg-id=\"\" tvg-logo=\"\",Kanal\nhttp://tv.invalid/13").single()
        assertNull(entry.tvgId)
        assertNull(entry.logo)
    }
}
