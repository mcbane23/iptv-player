package com.bane23.iptvplayer.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideMatchTest {

    private val guide = listOf(
        GuideChannel("maks.test", "Maks"),
        GuideChannel("maks2.test", "Maks 2"),
        GuideChannel("makssport.test", "Maks Sport"),
        GuideChannel("vesti24.test", "Vesti 24"),
        GuideChannel("decji.test", "Dečji svet"),
    )

    @Test
    fun `oznaka kvaliteta i drzave ne ulaze u poredjenje`() {
        assertEquals("maks", GuideMatch.normalize("Maks HD"))
        assertEquals("maks", GuideMatch.normalize("RS: Maks FHD"))
        assertEquals("maks", GuideMatch.normalize("|RS| Maks 4K"))
        assertEquals("maks", GuideMatch.normalize("[RS] Maks"))
        assertEquals("dečji svet", GuideMatch.normalize("Dečji-Svet (HD)"))
    }

    @Test
    fun `isti naziv bez oznake kvaliteta je prvi predlog`() {
        val found = GuideMatch.rank("Maks HD", guide)
        assertEquals("maks.test", found.first().key)
        assertEquals(setOf("maks.test", "maks2.test", "makssport.test"), found.map { it.key }.toSet())
    }

    @Test
    fun `duzi upit nalazi tacan kanal pre slicnih`() {
        assertEquals("makssport.test", GuideMatch.rank("RS: Maks Sport HD", guide).first().key)
    }

    @Test
    fun `poklapanje po jednoj reci se nudi posle boljih`() {
        val found = GuideMatch.rank("Sport Maks", guide)
        assertEquals("makssport.test", found.first().key)
        assertTrue(found.none { it.key == "vesti24.test" })
    }

    @Test
    fun `naziv bez slicnosti ne daje predloge`() {
        assertTrue(GuideMatch.rank("Planeta", guide).isEmpty())
    }

    @Test
    fun `prazan upit daje pocetak spiska po abecedi`() {
        assertEquals(listOf("Dečji svet", "Maks"), GuideMatch.rank("  ", guide, limit = 2).map { it.name })
    }

    @Test
    fun `id kanala iz vodica pomaze kad se naziv razlikuje`() {
        val odd = listOf(GuideChannel("vesti24.test", "Informativni kanal"))
        assertEquals("vesti24.test", GuideMatch.rank("Vesti 24", odd).first().key)
    }
}
