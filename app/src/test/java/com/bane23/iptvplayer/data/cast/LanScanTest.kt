package com.bane23.iptvplayer.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScanTest {

    @Test
    fun `scans the phone's own part of the network without the phone itself`() {
        val hosts = LanScan.hosts("192.168.1.37")
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.37" in hosts)
        assertTrue("192.168.1.36" in hosts)
    }

    @Test
    fun `other private ranges are scanned too`() {
        assertEquals("10.0.5.1", LanScan.hosts("10.0.5.20").first())
        assertEquals("172.20.3.1", LanScan.hosts("172.20.3.9").first())
    }

    @Test
    fun `public and malformed addresses are never scanned`() {
        assertTrue(LanScan.hosts("203.0.113.7").isEmpty())
        assertTrue(LanScan.hosts("172.32.0.5").isEmpty())
        assertTrue(LanScan.hosts("fe80::1").isEmpty())
        assertTrue(LanScan.hosts("192.168.1").isEmpty())
        assertTrue(LanScan.hosts("192.168.1.999").isEmpty())
    }
}
