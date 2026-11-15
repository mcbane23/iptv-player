package com.bane23.iptvplayer.data.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideLayoutTest {

    private fun layout(vararg programs: Pair<Long, Long>) =
        GuideLayout.cells(programs.toList(), from = 100, to = 200).map { Triple(it.index, it.startMs, it.stopMs) }

    @Test
    fun `empty row is one gap`() {
        assertEquals(listOf(Triple(null, 100L, 200L)), layout())
    }

    @Test
    fun `programmes fill the window`() {
        assertEquals(
            listOf(Triple(0, 100L, 150L), Triple(1, 150L, 200L)),
            layout(100L to 150L, 150L to 200L),
        )
    }

    @Test
    fun `programmes are clipped to the window and gaps are filled`() {
        assertEquals(
            listOf(
                Triple(0, 100L, 120L),
                Triple(null, 120L, 140L),
                Triple(1, 140L, 160L),
                Triple(null, 160L, 180L),
                Triple(2, 180L, 200L),
            ),
            layout(50L to 120L, 140L to 160L, 180L to 300L),
        )
    }

    @Test
    fun `overlapping and outside programmes are handled`() {
        assertEquals(
            listOf(Triple(1, 100L, 160L), Triple(2, 160L, 180L), Triple(null, 180L, 200L)),
            layout(10L to 90L, 90L to 160L, 150L to 180L, 120L to 170L, 250L to 300L),
        )
    }

    @Test
    fun `weights add up to one`() {
        val cells = GuideLayout.cells(listOf(110L to 130L, 150L to 190L), from = 100, to = 200)
        assertEquals(1f, cells.map { it.weight }.sum(), 0.0001f)
        assertEquals(0.2f, cells[1].weight, 0.0001f)
    }
}
