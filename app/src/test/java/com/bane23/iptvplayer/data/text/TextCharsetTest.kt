package com.bane23.iptvplayer.data.text

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class TextCharsetTest {
    private val central: Charset = Charset.forName("windows-1250")

    @Test
    fun `plain utf-8 text stays utf-8`() {
        assertEquals(Charsets.UTF_8, TextCharset.detect("#EXTM3U\n#EXTINF:-1,Izmišljeni kanal Šž".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `utf-8 sample cut in the middle of a letter is still utf-8`() {
        val bytes = "Kanal č".toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, TextCharset.detect(bytes, bytes.size - 1))
    }

    @Test
    fun `text that is not utf-8 is read as central european`() {
        val bytes = "#EXTM3U\n#EXTINF:-1,Izmišljeni kanal Čć".toByteArray(central)
        assertEquals(TextCharset.FALLBACK, TextCharset.detect(bytes))
        assertEquals("#EXTM3U\n#EXTINF:-1,Izmišljeni kanal Čć", TextCharset.reader(bytes.inputStream()).readText())
    }

    @Test
    fun `xml declaration decides the encoding`() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-2"?><tv><channel id="a"><display-name>Šž</display-name></channel></tv>"""
        val charset = Charset.forName("ISO-8859-2")
        assertEquals(charset, TextCharset.detect(xml.toByteArray(charset)))
        assertEquals(xml, TextCharset.reader(xml.toByteArray(charset).inputStream()).readText())
    }

    @Test
    fun `byte order mark wins`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "tekst".toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, TextCharset.detect(bytes))
    }

    @Test
    fun `empty input is utf-8`() {
        assertEquals(Charsets.UTF_8, TextCharset.detect(ByteArray(0)))
        assertEquals("", TextCharset.reader(ByteArray(0).inputStream()).readText())
    }
}
