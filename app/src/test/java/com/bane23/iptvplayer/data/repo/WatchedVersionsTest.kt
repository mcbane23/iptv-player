package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class WatchedVersionsTest {
    private fun series(id: Long, tmdbId: Long?) =
        VodItem(id = id, name = "Serija $id", poster = null, rating = null, extension = null, tmdbId = tmdbId)

    @Test
    fun `only the version watched in the app is kept`() {
        val all = listOf(series(1, 100), series(2, 100), series(3, 100), series(4, 200), series(5, 200))
        assertEquals(listOf(2L, 4L, 5L), watchedVersions(all, setOf(2L, 9L)).map { it.id })
    }

    @Test
    fun `all versions stay when none was watched in the app`() {
        val all = listOf(series(1, 100), series(2, 100))
        assertEquals(listOf(1L, 2L), watchedVersions(all, emptySet()).map { it.id })
    }
}
