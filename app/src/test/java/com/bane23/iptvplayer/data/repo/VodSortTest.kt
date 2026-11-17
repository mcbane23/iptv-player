package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class VodSortTest {

    private fun item(id: Long, name: String, rating: String? = null, added: Long = 0, year: Int = 0) =
        VodItem(id = id, name = name, poster = null, rating = rating, extension = null, added = added, year = year)

    private val items = listOf(
        item(1, "Zeleni film (1999)", rating = "6.5", added = 300),
        item(2, "Čudan film", rating = null, added = 100, year = 2020),
        item(3, "Brzi film (2024)", rating = "8.1", added = 0),
        item(4, "avantura", rating = "8.1", added = 200),
    )

    private fun ids(sort: VodSort) = sortVodItems(items, sort).map { it.id }

    @Test
    fun `default keeps provider order`() = assertEquals(listOf(1L, 2L, 3L, 4L), ids(VodSort.DEFAULT))

    @Test
    fun `added puts newest first and unknown last`() = assertEquals(listOf(1L, 4L, 2L, 3L), ids(VodSort.ADDED))

    @Test
    fun `name ignores case and sorts diacritics`() = assertEquals(listOf(4L, 3L, 2L, 1L), ids(VodSort.NAME))

    @Test
    fun `rating keeps provider order among equals and unrated last`() =
        assertEquals(listOf(3L, 4L, 1L, 2L), ids(VodSort.RATING))

    @Test
    fun `year falls back to the year in the name`() = assertEquals(listOf(3L, 2L, 1L, 4L), ids(VodSort.YEAR))

    @Test
    fun `unknown stored value means default`() {
        assertEquals(VodSort.DEFAULT, VodSort.of(null))
        assertEquals(VodSort.DEFAULT, VodSort.of("nesto"))
        assertEquals(VodSort.YEAR, VodSort.of("YEAR"))
    }
}
