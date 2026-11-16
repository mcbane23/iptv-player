package com.bane23.iptvplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryArrangeTest {

    private val names = listOf("Sport", "Filmovi", "Dokumentarni", "Muzika")

    @Test
    fun `bez sacuvanog redosleda spisak ostaje kakav je stigao`() {
        assertSame(names, CategoryArrange.ordered(names, emptyList()) { it })
    }

    @Test
    fun `sacuvani redosled se postuje a nove kategorije idu na kraj`() {
        val ordered = CategoryArrange.ordered(names, listOf("Muzika", "Nema je vise", "Sport")) { it }
        assertEquals(listOf("Muzika", "Sport", "Filmovi", "Dokumentarni"), ordered)
    }

    @Test
    fun `pomeranje za jedno mesto`() {
        assertEquals(listOf("Filmovi", "Sport", "Dokumentarni", "Muzika"), CategoryArrange.moved(names, 0, 1))
        assertEquals(listOf("Sport", "Filmovi", "Muzika", "Dokumentarni"), CategoryArrange.moved(names, 3, -1))
    }

    @Test
    fun `pomeranje van granica ne menja spisak`() {
        assertSame(names, CategoryArrange.moved(names, 0, -1))
        assertSame(names, CategoryArrange.moved(names, 3, 1))
        assertSame(names, CategoryArrange.moved(names, 7, 1))
    }

    @Test
    fun `na vrh`() {
        assertEquals(listOf("Dokumentarni", "Sport", "Filmovi", "Muzika"), CategoryArrange.movedToTop(names, 2))
        assertSame(names, CategoryArrange.movedToTop(names, 0))
    }

    @Test
    fun `sakrivene idu na dno bez mesanja ostalih`() {
        val arranged = CategoryArrange.withBottom(names, setOf("Sport", "Dokumentarni")) { it }
        assertEquals(listOf("Filmovi", "Muzika", "Sport", "Dokumentarni"), arranged)
    }
}
