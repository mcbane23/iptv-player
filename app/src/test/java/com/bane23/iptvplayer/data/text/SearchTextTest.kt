package com.bane23.iptvplayer.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `normalize drops case and marks`() {
        assertEquals("sasa i djordje", SearchText.normalize("  Šaša i Đorđe "))
    }

    @Test
    fun `one wrong letter still matches`() {
        assertTrue(SearchText.near("planeta zemlja", "planrta"))
    }

    @Test
    fun `one missing letter still matches`() {
        assertTrue(SearchText.near("planeta zemlja", "plneta"))
    }

    @Test
    fun `one extra letter still matches`() {
        assertTrue(SearchText.near("planeta zemlja", "plaaneta"))
    }

    @Test
    fun `two wrong letters do not match`() {
        assertFalse(SearchText.near("planeta zemlja", "plxnxta"))
    }

    @Test
    fun `short queries need an exact match`() {
        assertFalse(SearchText.near("vesti", "best"))
    }

    @Test
    fun `exact results come before close ones`() {
        val items = listOf("planina" to 1, "planeta dva" to 2, "plameta" to 3, "more" to 4, "planeta" to 5)
        assertEquals(listOf(2, 5, 3), SearchText.search(items, "planeta", limit = 10))
    }

    @Test
    fun `limit counts exact and close results together`() {
        val items = listOf("plameta" to 1, "planeta" to 2, "planeta dva" to 3, "plaketa" to 4)
        assertEquals(listOf(2, 3, 1), SearchText.search(items, "planeta", limit = 3))
        assertEquals(listOf(2, 3), SearchText.search(items, "planeta", limit = 2))
    }
}
