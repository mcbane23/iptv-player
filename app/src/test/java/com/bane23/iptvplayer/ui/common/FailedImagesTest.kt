package com.bane23.iptvplayer.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class FailedImagesTest {
    @Test
    fun `a refused image is skipped for a while and then tried again`() {
        val url = "http://primer.invalid/logo-odbijen.png"
        FailedImages.add(url, refused = true, now = 1_000)
        assertTrue(FailedImages.contains(url, now = 1_000 + 29 * 60_000L))
        assertFalse(FailedImages.contains(url, now = 1_000 + 31 * 60_000L))
    }

    @Test
    fun `an unreachable image is tried again sooner`() {
        val url = "http://primer.invalid/logo-bez-veze.png"
        FailedImages.add(url, refused = false, now = 1_000)
        assertTrue(FailedImages.contains(url, now = 1_000 + 60_000L))
        assertFalse(FailedImages.contains(url, now = 1_000 + 3 * 60_000L))
    }

    @Test
    fun `unknown and missing addresses are not marked`() {
        assertFalse(FailedImages.contains("http://primer.invalid/nepoznat.png"))
        assertFalse(FailedImages.contains(null))
        FailedImages.add("", refused = true)
        assertFalse(FailedImages.contains(""))
    }
}
