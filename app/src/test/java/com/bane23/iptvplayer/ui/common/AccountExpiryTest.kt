package com.bane23.iptvplayer.ui.common

import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.domain.PlaylistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountExpiryTest {

    private val day = 86_400_000L
    private val now = 1_000L * day

    private fun playlist(expiresAt: Long?) = Playlist(
        id = 1, name = "Test", type = PlaylistType.XTREAM, source = "http://example.invalid",
        username = "u", userAgent = null, channelCount = 0, lastRefreshedAt = null, isActive = false,
        accountExpiresAt = expiresAt,
    )

    @Test
    fun pastDateIsExpired() {
        val p = playlist(now - 3 * day)
        assertTrue(p.accountExpired(now))
        assertTrue(p.accountExpiresSoon(now))
    }

    @Test
    fun laterTodayIsNotExpired() {
        val p = playlist(now + day / 2)
        assertFalse(p.accountExpired(now))
        assertEquals(0, p.accountDaysLeft(now))
    }

    @Test
    fun unknownExpiryIsNeitherExpiredNorSoon() {
        val p = playlist(null)
        assertFalse(p.accountExpired(now))
        assertFalse(p.accountExpiresSoon(now))
    }
}
