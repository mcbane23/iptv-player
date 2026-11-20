package com.bane23.iptvplayer.data.repo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Svi podaci u testovima su izmišljeni. */
class VodDiskCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun cache(root: File = temp.root) = VodDiskCache(root)

    private val items = listOf(
        VodItem(id = 1, name = "Izmišljeni film", poster = "poster-a", rating = "7.4", extension = "mkv", tmdbId = 501, added = 1_700_000_000, year = 2021, categoryId = "10"),
        VodItem(id = 2, name = "Drugi", poster = null, rating = null, extension = null),
    )

    @Test
    fun `items are read back with stamp`() {
        val cache = cache()
        cache.writeItems(7, VodKind.MOVIES, "10", stamp = 123, items = items)
        val stored = cache.readItems(7, VodKind.MOVIES, "10")!!
        assertEquals(123L, stored.stamp)
        assertEquals(items, stored.value)
    }

    @Test
    fun `home row is kept apart from the lists and per kind`() {
        val cache = cache()
        cache.writeItems(7, VodKind.MOVIES, "", stamp = 1, items = items)
        cache.writeAdded(7, VodKind.MOVIES, signature = 42, items = items.take(1))
        val stored = cache.readAdded(7, VodKind.MOVIES)!!
        assertEquals(42L, stored.stamp)
        assertEquals(items.take(1), stored.value)
        assertEquals(items, cache.readItems(7, VodKind.MOVIES, "")!!.value)
        assertNull(cache.readAdded(7, VodKind.SERIES))
        assertNull(cache.readAdded(8, VodKind.MOVIES))
    }

    @Test
    fun `all list, other category, kind and playlist are kept apart`() {
        val cache = cache()
        cache.writeItems(7, VodKind.MOVIES, "", stamp = 1, items = items)
        assertEquals(items, cache.readItems(7, VodKind.MOVIES, "")!!.value)
        assertNull(cache.readItems(7, VodKind.MOVIES, "10"))
        assertNull(cache.readItems(7, VodKind.SERIES, ""))
        assertNull(cache.readItems(8, VodKind.MOVIES, ""))
    }

    @Test
    fun `category ids with unusual characters are safe file names`() {
        val cache = cache()
        cache.writeItems(7, VodKind.SERIES, "../a/b c", stamp = 1, items = items)
        assertEquals(items, cache.readItems(7, VodKind.SERIES, "../a/b c")!!.value)
    }

    @Test
    fun `categories are read back`() {
        val cache = cache()
        val categories = listOf(VodCategory("1", "Prva"), VodCategory("2", "Druga"))
        cache.writeCategories(7, VodKind.SERIES, stamp = 5, categories = categories)
        val stored = cache.readCategories(7, VodKind.SERIES)!!
        assertEquals(5L, stored.stamp)
        assertEquals(categories, stored.value)
    }

    @Test
    fun `broken file is ignored and removed`() {
        val cache = cache()
        cache.writeItems(7, VodKind.MOVIES, "10", stamp = 1, items = items)
        val file = temp.root.walkTopDown().first { it.isFile }
        file.writeText("pokvareno")
        assertNull(cache.readItems(7, VodKind.MOVIES, "10"))
        assertEquals(false, file.exists())
    }

    @Test
    fun `clear removes one playlist, clearAll removes everything`() {
        val cache = cache()
        cache.writeItems(7, VodKind.MOVIES, "", stamp = 1, items = items)
        cache.writeItems(8, VodKind.MOVIES, "", stamp = 1, items = items)
        cache.clear(7)
        assertNull(cache.readItems(7, VodKind.MOVIES, ""))
        assertEquals(items, cache.readItems(8, VodKind.MOVIES, "")!!.value)
        cache.clearAll()
        assertNull(cache.readItems(8, VodKind.MOVIES, ""))
    }
}
