package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class PreferredVersionsTest {
    private fun series(id: Long, tmdbId: Long?, name: String = "Serija $id", category: String? = null) =
        VodItem(id = id, name = name, poster = null, rating = null, extension = null, tmdbId = tmdbId, categoryId = category)

    @Test
    fun `only the version watched in the app is kept`() {
        val all = listOf(series(1, 100), series(2, 100), series(3, 100), series(4, 200), series(5, 200))
        assertEquals(listOf(2L, 4L), preferredVersions(all, setOf(2L, 9L)).map { it.id })
    }

    @Test
    fun `english version is preferred over other languages`() {
        val all = listOf(
            series(1, 100, "NL - Kancelarija (US)"),
            series(2, 100, "IT - Kancelarija (US)"),
            series(3, 100, "EN - Kancelarija"),
            series(4, 100, "DE - Kancelarija (US)"),
        )
        assertEquals(listOf(3L), preferredVersions(all, emptySet()).map { it.id })
    }

    @Test
    fun `english tags are ranked EN then UK then US`() {
        val all = listOf(series(1, 100, "US | Gradić"), series(2, 100, "[UK] Gradić"), series(3, 200, "US: Selo"), series(4, 200, "FR - Selo"))
        assertEquals(listOf(2L, 3L), preferredVersions(all, emptySet()).map { it.id })
    }

    @Test
    fun `first version is kept when none is english`() {
        val all = listOf(series(1, 100, "QC - Planina"), series(2, 100, "FR - Planina"), series(3, 100, "ES - Planina"))
        assertEquals(listOf(1L), preferredVersions(all, emptySet()).map { it.id })
    }

    @Test
    fun `category tells the language when the name has no tag`() {
        val all = listOf(series(1, 100, "Reka", "a"), series(2, 100, "Reka", "b"), series(3, 200, "Most", "a"), series(4, 200, "Most", "c"))
        val categories = mapOf("a" to "DE | Serije", "b" to "EN | Serije", "c" to "English series")
        assertEquals(listOf(2L, 4L), preferredVersions(all, emptySet(), categories).map { it.id })
    }

    @Test
    fun `tag in the name wins over the category`() {
        assertNull(englishRank("DE - Reka", "EN | Serije"))
        assertEquals(1, englishRank("GB - Reka", "DE | Serije"))
        assertNull(englishRank("Reka (US)", null))
    }
}
