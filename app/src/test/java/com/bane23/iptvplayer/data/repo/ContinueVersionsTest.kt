package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class ContinueVersionsTest {
    private fun item(id: Long, tmdbId: Long?, name: String, category: String? = null) =
        VodItem(id = id, name = name, poster = null, rating = null, extension = null, tmdbId = tmdbId, categoryId = category)

    private fun row(id: Long, updatedAt: Long, name: String = "Film $id") =
        ContinueItem(id = id, name = name, poster = null, extension = null, progress = 0.4f, isSeries = false, updatedAt = updatedAt)

    private val catalog = listOf(
        item(1, 100, "DE - Ostrvo"),
        item(2, 100, "EN - Ostrvo"),
        item(3, 100, "IT - Ostrvo"),
        item(4, 200, "FR - Planina"),
        item(5, 200, "PL - Planina"),
        item(6, 300, "EN - Reka"),
    ).associateBy { it.id }

    @Test
    fun `english version stays when none was played here`() {
        val rows = listOf(row(1, 50), row(2, 50), row(3, 50), row(6, 40))
        val result = oneVersionEach(rows, catalog, emptySet())
        assertEquals(listOf(2L, 6L), result.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), result.first().versionIds)
    }

    @Test
    fun `version played on this device wins over english`() {
        val rows = listOf(row(2, 50), row(3, 50), row(1, 50))
        val result = oneVersionEach(rows, catalog, setOf(3L, 9L))
        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test
    fun `most recent version is kept when none is english`() {
        val rows = listOf(row(5, 70), row(4, 60))
        assertEquals(listOf(5L), oneVersionEach(rows, catalog, emptySet()).map { it.id })
    }

    @Test
    fun `merged item keeps the place and time of the latest version`() {
        val rows = listOf(row(1, 90), row(6, 80), row(2, 70))
        val result = oneVersionEach(rows, catalog, emptySet())
        assertEquals(listOf(2L, 6L), result.map { it.id })
        assertEquals(90L, result.first().updatedAt)
    }

    @Test
    fun `items unknown to the list or without tmdb id are never merged`() {
        val withoutTmdb = mapOf(7L to item(7, null, "EN - Šuma"), 8L to item(8, null, "DE - Šuma"))
        val rows = listOf(row(7, 50), row(8, 50), row(9, 50), row(10, 50))
        assertEquals(listOf(7L, 8L, 9L, 10L), oneVersionEach(rows, withoutTmdb, emptySet()).map { it.id })
    }

    @Test
    fun `category tells the language when the name has no tag`() {
        val byCategory = listOf(item(1, 100, "Ostrvo", "a"), item(2, 100, "Ostrvo", "b")).associateBy { it.id }
        val rows = listOf(row(1, 50), row(2, 50))
        val result = oneVersionEach(rows, byCategory, emptySet(), mapOf("a" to "DE | Filmovi", "b" to "EN | Filmovi"))
        assertEquals(listOf(2L), result.map { it.id })
    }
}
