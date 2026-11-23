package com.bane23.iptvplayer.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverBackdropTest {

    @Test
    fun favoritesComeFirst() {
        val posters = screensaverPosters(
            favorites = listOf("https://primer.test/a.jpg", null, " ", "https://primer.test/a.jpg", "https://primer.test/b.jpg"),
            recent = listOf("https://primer.test/c.jpg"),
        )
        assertEquals(listOf("https://primer.test/a.jpg", "https://primer.test/b.jpg"), posters)
    }

    @Test
    fun recentWhenNoFavoritePosters() {
        val posters = screensaverPosters(favorites = listOf(null, ""), recent = listOf("https://primer.test/c.jpg", null))
        assertEquals(listOf("https://primer.test/c.jpg"), posters)
    }

    @Test
    fun emptyWhenNothing() {
        assertTrue(screensaverPosters(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun uniformImageOnlyGetsDarkerTowardEdges() {
        val width = 16
        val height = 9
        val pixels = IntArray(width * height) { 0xFFC8C8C8.toInt() }
        blurAndDim(pixels, width, height)
        val center = pixels[4 * width + 7] and 0xFF
        val corner = pixels[0] and 0xFF
        assertTrue(center in 90..101)
        assertTrue(corner < center)
        assertTrue(pixels.all { (it ushr 24) == 0xFF })
        // Boja ostaje siva, samo tamnija.
        val c = pixels[4 * width + 7]
        assertEquals((c shr 16) and 0xFF, (c shr 8) and 0xFF)
        assertEquals((c shr 8) and 0xFF, c and 0xFF)
    }

    @Test
    fun sharpEdgeIsSmoothed() {
        val width = 20
        val height = 3
        val pixels = IntArray(width * height) { i -> if (i % width < width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        blurAndDim(pixels, width, height, radius = 2)
        val left = pixels[width + width / 2 - 1] and 0xFF
        val right = pixels[width + width / 2] and 0xFF
        // Posle razlivanja nema skoka sa crnog na belo između susednih tačaka.
        assertTrue(left > 0)
        assertTrue(right - left < 60)
    }
}
