package com.bane23.iptvplayer.data.download

import com.bane23.iptvplayer.data.db.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRulesTest {

    @Test
    fun `playback holds downloads even on wifi`() {
        assertEquals(DownloadHold.PLAYING, DownloadRules.hold(playing = true, NetworkKind.WIFI, allowMobile = true))
    }

    @Test
    fun `mobile data holds downloads unless allowed`() {
        assertEquals(DownloadHold.NO_WIFI, DownloadRules.hold(playing = false, NetworkKind.MOBILE, allowMobile = false))
        assertNull(DownloadRules.hold(playing = false, NetworkKind.MOBILE, allowMobile = true))
    }

    @Test
    fun `wifi lets downloads run and no network holds them`() {
        assertNull(DownloadRules.hold(playing = false, NetworkKind.WIFI, allowMobile = false))
        assertEquals(DownloadHold.OFFLINE, DownloadRules.hold(playing = false, NetworkKind.NONE, allowMobile = true))
    }

    @Test
    fun `state follows the saved status first`() {
        assertEquals(DownloadState.DONE, DownloadRules.state(DownloadEntity.STATUS_DONE, active = false, DownloadHold.PLAYING))
        assertEquals(DownloadState.PAUSED, DownloadRules.state(DownloadEntity.STATUS_PAUSED, active = false, null))
        assertEquals(DownloadState.FAILED, DownloadRules.state(DownloadEntity.STATUS_FAILED, active = false, DownloadHold.NO_WIFI))
    }

    @Test
    fun `queued download shows why it waits`() {
        val queued = DownloadEntity.STATUS_QUEUED
        assertEquals(DownloadState.DOWNLOADING, DownloadRules.state(queued, active = true, null))
        assertEquals(DownloadState.WAITING, DownloadRules.state(queued, active = false, null))
        assertEquals(DownloadState.HELD_PLAYING, DownloadRules.state(queued, active = false, DownloadHold.PLAYING))
        assertEquals(DownloadState.HELD_WIFI, DownloadRules.state(queued, active = false, DownloadHold.NO_WIFI))
        assertEquals(DownloadState.HELD_OFFLINE, DownloadRules.state(queued, active = false, DownloadHold.OFFLINE))
    }

    @Test
    fun `partial response continues the file`() {
        assertEquals(ResumePlan.Append(1000), DownloadRules.resumePlan(400, 206, 600, "bytes 400-999/1000"))
        // Bez zaglavlja o opsegu veličina se računa iz dužine ostatka.
        assertEquals(ResumePlan.Append(1000), DownloadRules.resumePlan(400, 206, 600, null))
        assertEquals(ResumePlan.Append(0), DownloadRules.resumePlan(400, 206, -1, null))
    }

    @Test
    fun `partial response from another offset starts over`() {
        assertEquals(ResumePlan.StartOver, DownloadRules.resumePlan(400, 206, 1000, "bytes 0-999/1000"))
    }

    @Test
    fun `full response replaces what was downloaded`() {
        assertEquals(ResumePlan.Restart(1000), DownloadRules.resumePlan(400, 200, 1000, null))
        assertEquals(ResumePlan.Restart(0), DownloadRules.resumePlan(0, 200, -1, null))
    }

    @Test
    fun `range past the end means the file is complete only when sizes match`() {
        assertEquals(ResumePlan.Complete, DownloadRules.resumePlan(1000, 416, 0, "bytes */1000"))
        assertEquals(ResumePlan.StartOver, DownloadRules.resumePlan(1200, 416, 0, "bytes */1000"))
        assertEquals(ResumePlan.StartOver, DownloadRules.resumePlan(1000, 416, 0, null))
        assertNull(DownloadRules.resumePlan(0, 416, 0, null))
    }

    @Test
    fun `errors give no plan`() {
        assertNull(DownloadRules.resumePlan(0, 404, 0, null))
        assertNull(DownloadRules.resumePlan(400, 503, 0, null))
    }

    @Test
    fun `percent needs a known size`() {
        assertNull(DownloadRules.percent(500, 0))
        assertEquals(62, DownloadRules.percent(620, 1000))
        assertEquals(100, DownloadRules.percent(1500, 1000))
    }

    @Test
    fun `file name keeps only a safe extension`() {
        assertEquals("movie_3_42.mkv", DownloadRules.fileName(DownloadEntity.KIND_MOVIE, 3, 42, "MKV"))
        assertEquals("episode_3_42.mp4", DownloadRules.fileName(DownloadEntity.KIND_EPISODE, 3, 42, "../"))
        assertEquals("movie_1_7.mp4", DownloadRules.fileName(DownloadEntity.KIND_MOVIE, 1, 7, ""))
    }

    @Test
    fun `episode label has season and number`() {
        assertEquals("S1 E3", DownloadRules.episodeLabel(1, 3))
    }
}
