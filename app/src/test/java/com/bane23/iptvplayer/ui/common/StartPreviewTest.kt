package com.bane23.iptvplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPreviewTest {

    @Test
    fun `year is read from any date shape`() {
        assertEquals(2021, releaseYear("2021-05-14"))
        assertEquals(1998, releaseYear("14.05.1998"))
        assertEquals(0, releaseYear(""))
        assertEquals(0, releaseYear(null))
        assertEquals(0, releaseYear("uskoro"))
    }

    @Test
    fun `empty and zero rating are not shown`() {
        assertNull(shownRating(null))
        assertNull(shownRating(" "))
        assertNull(shownRating("0"))
        assertNull(shownRating("0.0"))
        assertEquals("7.8", shownRating(" 7.8 "))
        assertEquals("6,5", shownRating("6,5"))
    }

    @Test
    fun `details fill what the list did not have`() {
        val basic = StartPreview(key = "m1", title = "Izmišljeni film", image = "poster", year = 2020, rating = "7.1")
        val full = basic.withDetails(plot = " Opis. ", genre = "Drama", releaseDate = "1999-01-01", rating = "3", backdrop = "siroka")
        assertEquals("Opis.", full.text)
        assertEquals("Drama", full.genre)
        assertEquals(2020, full.year)
        assertEquals("7.1", full.rating)
        assertEquals("siroka", full.image)
        assertTrue(full.wide)
    }

    @Test
    fun `without a wide picture the poster stays`() {
        val basic = StartPreview(key = "s2", title = "Izmišljena serija", image = "poster")
        val full = basic.withDetails(plot = "", genre = null, releaseDate = "2015", rating = "8.2", backdrop = " ")
        assertNull(full.text)
        assertEquals(2015, full.year)
        assertEquals("8.2", full.rating)
        assertEquals("poster", full.image)
        assertFalse(full.wide)
    }
}
