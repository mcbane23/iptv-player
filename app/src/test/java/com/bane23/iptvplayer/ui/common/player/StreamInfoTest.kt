package com.bane23.iptvplayer.ui.common.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamInfoTest {
    @Test
    fun `video line joins known parts`() {
        assertEquals(
            "1920×1080  ·  25 fps  ·  H.264  ·  4.2 Mb/s",
            videoInfoLine(1920, 1080, 25f, "video/avc", 4_200_000),
        )
    }

    @Test
    fun `video line skips unknown parts`() {
        assertEquals("1280×720  ·  H.265", videoInfoLine(1280, 720, -1f, "video/hevc", -1))
        assertNull(videoInfoLine(0, 0, 0f, null, 0))
    }

    @Test
    fun `fractional frame rate keeps two decimals`() {
        assertEquals("29.97 fps", frameRateLabel(29.97f))
        assertEquals("50 fps", frameRateLabel(50f))
    }

    @Test
    fun `audio line shows codec channels rate and bitrate`() {
        assertEquals("AAC  ·  2.0  ·  48 kHz  ·  128 kb/s", audioInfoLine("audio/mp4a-latm", 2, 48_000, 128_000))
        assertEquals("E-AC-3  ·  5.1  ·  44.1 kHz", audioInfoLine("audio/eac3", 6, 44_100, 0))
    }

    @Test
    fun `unknown codec falls back to mime subtype`() {
        assertEquals("X-FOO", codecLabel("video/x-foo"))
    }
}
