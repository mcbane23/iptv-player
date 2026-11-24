package com.bane23.iptvplayer.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {

    @Test
    fun `newest query goes first`() {
        assertEquals(listOf("vesti", "sport"), RecentSearches.add(listOf("sport"), "vesti"))
    }

    @Test
    fun `repeated query moves to the top without a duplicate`() {
        assertEquals(listOf("Sport", "vesti"), RecentSearches.add(listOf("vesti", "sport"), " Sport "))
    }

    @Test
    fun `blank query changes nothing`() {
        assertEquals(listOf("vesti"), RecentSearches.add(listOf("vesti"), "   "))
    }

    @Test
    fun `list keeps only the newest entries`() {
        var list = emptyList<String>()
        for (i in 1..12) list = RecentSearches.add(list, "upit $i")
        assertEquals(RecentSearches.LIMIT, list.size)
        assertEquals("upit 12", list.first())
        assertEquals("upit 5", list.last())
    }

    @Test
    fun `encode and decode keep the order`() {
        val list = listOf("vesti u podne", "sport", "film")
        assertEquals(list, RecentSearches.decode(RecentSearches.encode(list)))
        assertEquals(emptyList<String>(), RecentSearches.decode(null))
    }

    @Test
    fun `remove drops only the given query`() {
        assertEquals(listOf("vesti", "film"), RecentSearches.remove(listOf("vesti", "Sport", "film"), " sport "))
        assertEquals(listOf("vesti"), RecentSearches.remove(listOf("vesti"), "nema"))
    }
}
