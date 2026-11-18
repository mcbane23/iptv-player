package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsTest {

    private fun item(id: Long, added: Long, category: String? = null, tmdb: Long? = null) =
        VodItem(id = id, name = "Film $id", poster = null, rating = null, extension = null, added = added, categoryId = category, tmdbId = tmdb)

    private fun progress(id: Long, updatedAt: Long, series: Boolean) =
        ContinueItem(id = id, name = "Naslov $id", poster = null, extension = null, progress = 0.5f, isSeries = series, updatedAt = updatedAt)

    @Test
    fun `recently added is newest first and limited`() {
        val items = listOf(item(1, 100), item(2, 300), item(3, 200), item(4, 50))
        assertEquals(listOf(2L, 3L, 1L), recentlyAdded(items, emptySet(), 3).map { it.id })
    }

    @Test
    fun `items without a time are skipped`() {
        assertEquals(emptyList<VodItem>(), recentlyAdded(listOf(item(1, 0), item(2, 0)), emptySet(), 10))
    }

    @Test
    fun `hidden categories are left out`() {
        val items = listOf(item(1, 100, "a"), item(2, 300, "b"), item(3, 200))
        assertEquals(listOf(3L, 1L), recentlyAdded(items, setOf("b"), 10).map { it.id })
    }

    @Test
    fun `versions of the same title appear once`() {
        val items = listOf(item(1, 100, tmdb = 7), item(2, 300, tmdb = 7), item(3, 200))
        assertEquals(listOf(2L, 3L), recentlyAdded(items, emptySet(), 10).map { it.id })
    }

    @Test
    fun `continue row mixes movies and series by last watched`() {
        val movies = listOf(progress(1, 500, false), progress(2, 100, false))
        val series = listOf(progress(9, 300, true))
        assertEquals(listOf(1L, 9L, 2L), mergeContinue(movies, series, 10).map { it.id })
        assertEquals(listOf(1L), mergeContinue(movies, series, 1).map { it.id })
    }
}
