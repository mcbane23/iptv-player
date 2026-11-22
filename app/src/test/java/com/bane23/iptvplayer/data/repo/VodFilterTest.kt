package com.bane23.iptvplayer.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class VodFilterTest {
    private fun item(id: Long, name: String, rating: String? = null, year: Int = 0) =
        VodItem(id = id, name = name, poster = null, rating = rating, extension = "mp4", year = year)

    private val items = listOf(
        item(1, "Prvi film", rating = "7.4", year = 2021),
        item(2, "Drugi film (2015)", rating = "8.1"),
        item(3, "Treci film 4K", rating = "5.9", year = 2023),
        item(4, "Cetvrti film"),
        item(5, "Peti film UHD", rating = "9", year = 1995),
    )

    private fun ids(filter: VodFilter) = filterVodItems(items, filter).map { it.id }

    @Test
    fun `no filter returns the same list`() {
        assertFalse(VodFilter().active)
        assertSame(items, filterVodItems(items, VodFilter()))
    }

    @Test
    fun `years use the list year or the year in the name`() {
        assertEquals(listOf(1L, 3L), ids(VodFilter(years = VodYears.FROM_2020)))
        assertEquals(listOf(2L), ids(VodFilter(years = VodYears.FROM_2010)))
        assertEquals(listOf(5L), ids(VodFilter(years = VodYears.OLDER)))
    }

    @Test
    fun `items without a rating do not pass a rating filter`() {
        assertEquals(listOf(1L, 2L, 5L), ids(VodFilter(minRating = 7)))
        assertEquals(listOf(2L, 5L), ids(VodFilter(minRating = 8)))
    }

    @Test
    fun `only 4k reads the name`() {
        assertEquals(listOf(3L, 5L), ids(VodFilter(only4k = true)))
    }

    @Test
    fun `conditions combine`() {
        assertEquals(listOf(1L), ids(VodFilter(years = VodYears.FROM_2020, minRating = 7)))
        assertEquals(2, VodFilter(years = VodYears.FROM_2020, minRating = 7).count)
    }

    @Test
    fun `filter survives encoding`() {
        val filter = VodFilter(VodYears.FROM_2000, 8, true)
        assertEquals(filter, VodFilter.decode(filter.encode()))
        assertEquals(VodFilter(), VodFilter.decode(null))
        assertEquals(VodFilter(), VodFilter.decode("nesto|x"))
    }
}
