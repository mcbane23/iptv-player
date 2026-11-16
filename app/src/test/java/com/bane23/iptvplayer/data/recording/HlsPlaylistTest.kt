package com.bane23.iptvplayer.data.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistTest {

    private val base = "http://tv.example.invalid/live/kanal/index.m3u8?t=1"

    @Test
    fun `master playlist lists variants best first`() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=2500000,CODECS="avc1.4d401f,mp4a.40.2"
            http://cdn.example.invalid/high/index.m3u8
            """.trimIndent(),
            base,
        )
        assertEquals(
            listOf("http://cdn.example.invalid/high/index.m3u8", "http://tv.example.invalid/live/kanal/low/index.m3u8"),
            playlist.variants,
        )
        assertTrue(playlist.segments.isEmpty())
    }

    @Test
    fun `media playlist numbers segments from the media sequence`() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:120
            #EXTINF:10.0,
            seg120.ts
            #EXTINF:10.0,
            /abs/seg121.ts
            """.trimIndent(),
            base,
        )
        assertEquals(
            listOf(
                HlsSegment(120, "http://tv.example.invalid/live/kanal/seg120.ts"),
                HlsSegment(121, "http://tv.example.invalid/abs/seg121.ts"),
            ),
            playlist.segments,
        )
        assertEquals(10, playlist.targetDurationSeconds)
        assertFalse(playlist.ended)
        assertFalse(playlist.unsupported)
    }

    @Test
    fun `end of stream is recognized`() {
        val playlist = HlsPlaylist.parse("#EXTM3U\n#EXTINF:4,\na.ts\n#EXT-X-ENDLIST\n", base)
        assertTrue(playlist.ended)
        assertEquals(0L, playlist.segments.single().sequence)
    }

    @Test
    fun `encrypted and fmp4 streams are not supported`() {
        val encrypted = HlsPlaylist.parse(
            "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:4,\na.ts\n",
            base,
        )
        assertTrue(encrypted.unsupported)
        val fmp4 = HlsPlaylist.parse("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4,\na.m4s\n", base)
        assertTrue(fmp4.unsupported)
        val clear = HlsPlaylist.parse("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:4,\na.ts\n", base)
        assertFalse(clear.unsupported)
    }

    @Test
    fun `playlist text is recognized`() {
        assertTrue(HlsPlaylist.isPlaylist("\uFEFF#EXTM3U\n"))
        assertFalse(HlsPlaylist.isPlaylist("G@\u0000binary"))
    }
}
