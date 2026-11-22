package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.domain.ArchiveProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveMatchTest {
    private val hour = 3_600_000L
    private val programs = listOf(
        ArchiveProgram(startMs = 0, stopMs = hour, title = "Prva", description = null, startToken = "a"),
        ArchiveProgram(startMs = hour, stopMs = 2 * hour, title = "Druga", description = null, startToken = "b"),
    )

    @Test
    fun `picks the program with the same slot`() {
        assertEquals("b", ArchiveMatch.best(programs, hour, 2 * hour)?.startToken)
    }

    @Test
    fun `tolerates a small shift between guide and archive`() {
        assertEquals("b", ArchiveMatch.best(programs, hour + 300_000, 2 * hour + 300_000)?.startToken)
    }

    @Test
    fun `no program when less than half of the slot is covered`() {
        assertNull(ArchiveMatch.best(programs, 2 * hour - 600_000, 3 * hour)?.startToken)
        assertNull(ArchiveMatch.best(emptyList(), 0, hour))
    }
}
