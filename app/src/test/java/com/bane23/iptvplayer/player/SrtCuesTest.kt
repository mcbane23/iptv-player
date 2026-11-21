package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SrtCuesTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        Prvi red, kaže: 12:30:00,000

        2
        00:59:59,750 --> 01:00:02,000
        <i>Čekaj</i> --> ovo nije vreme
        {\an8}drugi <font color="#ffffff">red</font>
    """.trimIndent()

    @Test
    fun `reads times and text`() {
        val cues = SrtCues.parse(srt).cues
        assertEquals(2, cues.size)
        assertEquals(SubtitleCue(1_000, 3_500, "Prvi red, kaže: 12:30:00,000"), cues[0])
        assertEquals(3_599_750, cues[1].startMs)
        assertEquals(3_602_000, cues[1].endMs)
    }

    @Test
    fun `style tags are removed and lines kept`() {
        assertEquals("Čekaj --> ovo nije vreme\ndrugi red", SrtCues.parse(srt).cues[1].text)
    }

    @Test
    fun `text is shown only between start and end`() {
        val cues = SrtCues.parse(srt)
        assertNull(cues.textAt(999))
        assertEquals("Prvi red, kaže: 12:30:00,000", cues.textAt(1_000))
        assertEquals("Prvi red, kaže: 12:30:00,000", cues.textAt(3_499))
        assertNull(cues.textAt(3_500))
        assertNull(cues.textAt(10_000))
    }

    @Test
    fun `overlapping cues are shown together`() {
        val cues = SrtCues.parse("1\n00:00:01,000 --> 00:00:05,000\ndole\n\n2\n00:00:02,000 --> 00:00:03,000\ngore\n")
        assertEquals("dole\ngore", cues.textAt(2_500))
        assertEquals("dole", cues.textAt(4_000))
    }

    @Test
    fun `windows line ends, dot fractions and missing blank lines are accepted`() {
        val cues = SrtCues.parse("﻿1\r\n00:00:01.5 --> 00:00:02.25\r\njedan\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\ndva\r\n").cues
        assertEquals(listOf(SubtitleCue(1_500, 2_250, "jedan"), SubtitleCue(3_000, 4_000, "dva")), cues)
    }

    @Test
    fun `broken and empty cues are skipped`() {
        val cues = SrtCues.parse("1\n00:00:05,000 --> 00:00:04,000\nunazad\n\n2\n00:00:06,000 --> 00:00:07,000\n<i></i>\n\nbez vremena\n").cues
        assertEquals(emptyList<SubtitleCue>(), cues)
        assertNull(SrtCues.parse("").textAt(0))
    }

    @Test
    fun `decodes utf8, utf16 and central european files`() {
        val text = "čćžšđ ČĆŽŠĐ"
        assertEquals(text, SrtCues.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, SrtCues.decode(text.toByteArray(Charsets.UTF_16)))
        assertEquals(text, SrtCues.decode(text.toByteArray(charset("windows-1250"))))
    }
}
