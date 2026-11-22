package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SeekAnchorsTest {
    private val second = 1_000_000L

    @Test
    fun `known duration spreads time evenly over the file`() {
        val anchors = SeekAnchors(length = 1_000_000, fileDurationUs = 100 * second)
        assertEquals(0, anchors.positionFor(0))
        assertEquals(500_000, anchors.positionFor(50 * second))
        assertEquals(999_999, anchors.positionFor(100 * second))
        assertEquals(100 * second, anchors.durationUs())
    }

    @Test
    fun `unknown duration is estimated from the furthest known point`() {
        val anchors = SeekAnchors(length = 1_000_000)
        assertEquals(SeekAnchors.UNKNOWN, anchors.durationUs())
        assertEquals(0, anchors.positionFor(30 * second))
        anchors.add(100_000, 20 * second)
        assertEquals(200 * second, anchors.durationUs())
        assertEquals(500_000.0, anchors.positionFor(100 * second).toDouble(), 1.0)
        anchors.add(600_000, 100 * second)
        assertEquals(166_666_666.0, anchors.durationUs().toDouble(), 1.0)
    }

    @Test
    fun `position is taken between the two nearest known points`() {
        val anchors = SeekAnchors(length = 1_000_000, fileDurationUs = 100 * second)
        anchors.add(200_000, 40 * second)
        anchors.add(800_000, 60 * second)
        assertEquals(100_000.0, anchors.positionFor(20 * second).toDouble(), 1.0)
        assertEquals(500_000.0, anchors.positionFor(50 * second).toDouble(), 1.0)
        assertEquals(900_000.0, anchors.positionFor(80 * second).toDouble(), 1.0)
        assertEquals(200_000, anchors.positionFor(40 * second))
    }

    @Test
    fun `points that contradict known ones are ignored`() {
        val anchors = SeekAnchors(length = 1_000_000, fileDurationUs = 100 * second)
        anchors.add(500_000, 50 * second)
        anchors.add(400_000, 60 * second)
        anchors.add(600_000, 40 * second)
        anchors.add(2_000_000, 70 * second)
        anchors.add(700_000, 200 * second)
        assertEquals(250_000.0, anchors.positionFor(25 * second).toDouble(), 1.0)
        assertEquals(750_000.0, anchors.positionFor(75 * second).toDouble(), 1.0)
    }

    /** Izmišljen fajl sa neravnomernim protokom: negde ide brže, negde sporije od proseka. */
    private fun timeAt(position: Long, length: Long, durationUs: Long): Long {
        val x = position.toDouble() / length
        return (durationUs * (x + 0.03 * (sin(2 * PI * 5 * x + 0.7) - sin(0.7)))).toLong()
    }

    @Test
    fun `a few corrections land close to the target in an uneven file`() {
        val length = 800_000_000L
        val duration = 2_700 * second
        for (known in listOf(duration, SeekAnchors.UNKNOWN)) {
            val anchors = SeekAnchors(length, known)
            // Kao posle prvih 20 sekundi gledanja.
            anchors.add(1_500_000, timeAt(1_500_000, length, duration))
            for (target in listOf(300 * second, 1_300 * second, 2_000 * second, 900 * second, 2_500 * second, 60 * second)) {
                var landed = 0L
                var tries = 0
                do {
                    val position = anchors.positionFor(target)
                    landed = timeAt(position, length, duration)
                    anchors.add(position, landed)
                    tries++
                } while (!SeekLanding.isCloseEnough(landed, target) && tries < 5)
                assertTrue("target $target landed $landed after $tries tries", SeekLanding.isCloseEnough(landed, target))
            }
        }
    }
}
