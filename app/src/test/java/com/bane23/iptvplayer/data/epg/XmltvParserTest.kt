package com.bane23.iptvplayer.data.epg

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvParserTest {

    private fun parse(xml: String): List<XmltvItem> = XmltvParser.parse(StringReader(xml)).toList()

    private fun programs(xml: String) = parse(xml).filterIsInstance<XmltvItem.Program>()

    @Test
    fun `reads channels and programmes`() {
        val items = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv generator-info-name="test">
              <channel id="news.example">
                <display-name lang="en">Example News</display-name>
                <display-name>Example News HD</display-name>
                <icon src="http://logos.invalid/news.png"/>
              </channel>
              <programme start="20260115180000 +0000" stop="20260115190000 +0000" channel="news.example">
                <title lang="en">Evening News</title>
                <desc lang="en">Today's headlines.</desc>
                <category>News</category>
              </programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                XmltvItem.Channel("news.example", listOf("Example News", "Example News HD")),
                XmltvItem.Program(
                    channelId = "news.example",
                    startMs = 1_768_500_000_000,
                    stopMs = 1_768_503_600_000,
                    title = "Evening News",
                    description = "Today's headlines.",
                ),
            ),
            items,
        )
    }

    @Test
    fun `decodes entities and cdata`() {
        val program = programs(
            """
            <tv><programme start="20260115180000" stop="20260115183000" channel="a&amp;b">
              <title>Tom &amp; Jerry &lt;3 &#233;t&#xE9; &unknown; R&D</title>
              <desc><![CDATA[Raw <b>text</b> & more ]] here]]></desc>
            </programme></tv>
            """.trimIndent(),
        ).single()
        assertEquals("a&b", program.channelId)
        assertEquals("Tom & Jerry <3 été &unknown; R&D", program.title)
        assertEquals("Raw <b>text</b> & more ]] here", program.description)
    }

    @Test
    fun `uses first title and ignores nested markup and comments`() {
        val program = programs(
            """
            <tv>
              <!-- a comment with <programme> inside -->
              <programme channel="c1" stop="20260115183000" start="20260115180000">
                <title lang="sr">Prvi <i>naslov</i></title>
                <title lang="en">Second title</title>
                <credits><actor>Someone</actor><title>Not a title</title></credits>
                <desc/>
              </programme>
            </tv>
            """.trimIndent(),
        ).single()
        assertEquals("Prvi naslov", program.title)
        assertNull(program.description)
    }

    @Test
    fun `skips programmes that are incomplete`() {
        val result = programs(
            """
            <tv>
              <programme start="20260115180000" channel="c1"><title>No stop</title></programme>
              <programme start="20260115180000" stop="20260115170000" channel="c1"><title>Ends before start</title></programme>
              <programme start="bad" stop="20260115190000" channel="c1"><title>Bad start</title></programme>
              <programme start="20260115180000" stop="20260115190000"><title>No channel</title></programme>
              <programme start="20260115180000" stop="20260115190000" channel="c1"><title> </title></programme>
              <programme start="20260115180000" stop="20260115190000" channel="c1"/>
              <programme start="20260115180000" stop="20260115190000" channel="c1"><title>Good</title></programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals(listOf("Good"), result.map { it.title })
    }

    @Test
    fun `survives input that is cut off`() {
        val result = programs(
            """
            <tv>
              <programme start="20260115180000" stop="20260115190000" channel="c1"><title>Whole</title></programme>
              <programme start="20260115190000" stop="20260115200000" channel="c1"><title>Cut of
            """.trimIndent(),
        )
        assertEquals(listOf("Whole"), result.map { it.title })
    }

    @Test
    fun `reads large input across buffer boundaries`() {
        val count = 5_000
        val xml = buildString {
            append("<tv>")
            repeat(count) { i ->
                append("<programme start=\"20260115180000\" stop=\"20260115190000\" channel=\"c$i\">")
                append("<title>Show &amp; tell $i</title><desc><![CDATA[Part $i]]></desc></programme>\n")
            }
            append("</tv>")
        }
        val result = programs(xml)
        assertEquals(count, result.size)
        result.forEachIndexed { i, program ->
            assertEquals("c$i", program.channelId)
            assertEquals("Show & tell $i", program.title)
            assertEquals("Part $i", program.description)
        }
    }

    @Test
    fun `parses time with zones and short forms`() {
        assertEquals(1_768_500_000_000, XmltvParser.parseTime("20260115180000 +0000"))
        assertEquals(1_768_500_000_000, XmltvParser.parseTime("20260115180000"))
        assertEquals(1_768_500_000_000, XmltvParser.parseTime("20260115190000 +0100"))
        assertEquals(1_768_500_000_000, XmltvParser.parseTime("20260115123000 -0530"))
        assertEquals(1_768_500_000_000, XmltvParser.parseTime("20260115190000 +01:00"))
        assertEquals(1_768_500_000_000, XmltvParser.parseTime("202601151800 UTC"))
        assertEquals(1_768_435_200_000, XmltvParser.parseTime("20260115"))
        assertEquals(951_782_400_000, XmltvParser.parseTime("20000229000000 +0000"))
        assertEquals(0L, XmltvParser.parseTime("19700101000000 +0000"))
    }

    @Test
    fun `rejects invalid time`() {
        assertNull(XmltvParser.parseTime(""))
        assertNull(XmltvParser.parseTime("2026"))
        assertNull(XmltvParser.parseTime("20261315180000"))
        assertNull(XmltvParser.parseTime("20260115250000"))
        assertNull(XmltvParser.parseTime("20260115180000 CET"))
        assertNull(XmltvParser.parseTime("20260115180000 +01"))
    }
}
