package com.bane23.iptvplayer.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val sha = "a".repeat(64)

    @Test
    fun parsesManifestFromCi() {
        val m = UpdateManifestParser.parse(
            """{"versionCode":42,"versionName":"0.0.1-dev.42","apk":"iptv-player-dev.apk",
               "sha256":"${sha.uppercase()}","commit":"abc1234","extra":true}""",
        )
        assertNotNull(m)
        assertEquals(42L, m!!.versionCode)
        assertEquals("0.0.1-dev.42", m.versionName)
        assertEquals("iptv-player-dev.apk", m.apk)
        // Heš se svodi na mala slova, da poređenje posle preuzimanja radi.
        assertEquals(sha, m.sha256)
    }

    @Test
    fun sha256IsOptional() {
        val m = UpdateManifestParser.parse("""{"versionCode":5,"versionName":"x","apk":"a.apk"}""")
        assertNotNull(m)
        assertNull(m!!.sha256)
    }

    @Test
    fun rejectsBrokenOrSuspiciousManifests() {
        assertNull(UpdateManifestParser.parse("not json"))
        assertNull(UpdateManifestParser.parse("""{"versionName":"x","apk":"a.apk"}"""))
        assertNull(UpdateManifestParser.parse("""{"versionCode":0,"versionName":"x","apk":"a.apk"}"""))
        assertNull(UpdateManifestParser.parse("""{"versionCode":3,"versionName":"x","apk":"../a.apk"}"""))
        assertNull(UpdateManifestParser.parse("""{"versionCode":3,"versionName":"x","apk":"a.zip"}"""))
        assertNull(UpdateManifestParser.parse("""{"versionCode":3,"versionName":"x","apk":"a.apk","sha256":"xyz"}"""))
    }

    @Test
    fun onlyHigherVersionCodeIsAnUpdate() {
        val m = UpdateManifest(versionCode = 10, versionName = "x", apk = "a.apk")
        assertTrue(UpdateManifestParser.isNewer(m, 9))
        assertFalse(UpdateManifestParser.isNewer(m, 10))
        assertFalse(UpdateManifestParser.isNewer(m, 11))
    }

    @Test
    fun urlsPointToChannelRelease() {
        val m = UpdateManifest(versionCode = 1, versionName = "x", apk = "iptv-player-dev.apk")
        assertTrue(UpdateSource.manifestUrl(UpdateChannel.DEV).endsWith("/releases/download/iptv-player-dev/iptv-player.json"))
        assertTrue(UpdateSource.manifestUrl(UpdateChannel.RELEASE).endsWith("/releases/download/iptv-player/iptv-player.json"))
        assertTrue(UpdateSource.apkUrl(UpdateChannel.DEV, m).endsWith("/iptv-player-dev/iptv-player-dev.apk"))
    }

    @Test
    fun channelIsFoundByTag() {
        assertEquals(UpdateChannel.DEV, UpdateChannel.fromTag("iptv-player-dev"))
        assertEquals(UpdateChannel.RELEASE, UpdateChannel.fromTag("iptv-player"))
        assertNull(UpdateChannel.fromTag("nešto drugo"))
        assertNull(UpdateChannel.fromTag(null))
    }
}
