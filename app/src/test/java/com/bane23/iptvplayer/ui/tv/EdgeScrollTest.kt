package com.bane23.iptvplayer.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeScrollTest {
    @Test
    fun `red u sredini ne pomera spisak`() {
        assertEquals(0f, edgeScrollDistance(offset = 300f, size = 40f, containerSize = 700f, margin = 50f), 0f)
        assertEquals(0f, edgeScrollDistance(offset = 50f, size = 40f, containerSize = 700f, margin = 50f), 0f)
        assertEquals(0f, edgeScrollDistance(offset = 610f, size = 40f, containerSize = 700f, margin = 50f), 0f)
    }

    @Test
    fun `red uz donju ivicu pomera spisak taman da ostane razmak`() {
        assertEquals(30f, edgeScrollDistance(offset = 640f, size = 40f, containerSize = 700f, margin = 50f), 0f)
        assertEquals(90f, edgeScrollDistance(offset = 700f, size = 40f, containerSize = 700f, margin = 50f), 0f)
    }

    @Test
    fun `red uz gornju ivicu pomera spisak unazad`() {
        assertEquals(-30f, edgeScrollDistance(offset = 20f, size = 40f, containerSize = 700f, margin = 50f), 0f)
        assertEquals(-90f, edgeScrollDistance(offset = -40f, size = 40f, containerSize = 700f, margin = 50f), 0f)
    }

    @Test
    fun `posle pomeranja zahtev je ispunjen`() {
        val distance = edgeScrollDistance(offset = 680f, size = 40f, containerSize = 700f, margin = 50f)
        assertEquals(0f, edgeScrollDistance(680f - distance, 40f, 700f, 50f), 0f)
    }

    @Test
    fun `razmak se smanjuje kad element jedva staje`() {
        // Staje samo 10 piksela razmaka sa svake strane.
        assertEquals(0f, edgeScrollDistance(offset = 10f, size = 80f, containerSize = 100f, margin = 50f), 0f)
        assertEquals(20f, edgeScrollDistance(offset = 30f, size = 80f, containerSize = 100f, margin = 50f), 0f)
    }

    @Test
    fun `element visi od spiska koji ga ispunjava ostaje na mestu`() {
        assertEquals(0f, edgeScrollDistance(offset = -20f, size = 200f, containerSize = 100f, margin = 50f), 0f)
    }
}
