package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MkvClustersTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** Početak bloka: oznaka, veličina (nepoznata, 8 bajtova), pa vreme od dva bajta. */
    private fun cluster(timecode: Int) =
        bytes(0x1F, 0x43, 0xB6, 0x75, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xE7, 0x82, timecode shr 8, timecode and 0xFF)

    private val padding = ByteArray(MkvClusters.HEADER_BYTES)

    @Test
    fun `finds the first cluster and reads its time`() {
        val noise = ByteArray(100) { (it * 7).toByte() }
        val data = noise + cluster(0x1234) + padding
        val at = MkvClusters.find(data, data.size)
        assertEquals(100, at)
        assertEquals(0x1234L, MkvClusters.timecode(data, at))
    }

    @Test
    fun `the same four bytes inside picture data are not taken for a cluster`() {
        val fake = bytes(0x1F, 0x43, 0xB6, 0x75, 0x00, 0x11, 0x22, 0x33) + bytes(0x1F, 0x43, 0xB6, 0x75, 0x81, 0x55)
        val data = fake + ByteArray(40) + cluster(7) + padding
        assertEquals(fake.size + 40, MkvClusters.find(data, data.size))
    }

    @Test
    fun `a cluster cut off by the end of the buffer is left for the next round`() {
        val data = ByteArray(50) + cluster(1)
        assertEquals(-1, MkvClusters.find(data, data.size))
        assertEquals(-1, MkvClusters.find(ByteArray(10), 10))
    }

    @Test
    fun `cluster that does not start with time has no time`() {
        val data = bytes(0x1F, 0x43, 0xB6, 0x75, 0x84, 0xBF, 0x84, 1, 2, 3, 4) + padding
        assertEquals(0, MkvClusters.find(data, data.size))
        assertEquals(-1L, MkvClusters.timecode(data, 0))
    }

    @Test
    fun `time unit is read from the header or defaults to a millisecond`() {
        val header = ByteArray(20) + bytes(0x2A, 0xD7, 0xB1, 0x83, 0x07, 0xA1, 0x20) + ByteArray(20)
        assertEquals(500_000L, MkvClusters.timecodeScaleNs(header, header.size))
        assertEquals(1_000_000L, MkvClusters.timecodeScaleNs(ByteArray(64), 64))
    }
}
