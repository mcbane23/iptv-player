package com.bane23.iptvplayer.domain

import com.bane23.iptvplayer.domain.ChannelRules.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRulesTest {

    @Test
    fun `quality is read from the name`() {
        assertEquals(0, ChannelRules.quality("Prvi kanal"))
        assertEquals(0, ChannelRules.quality("Prvi kanal SD"))
        assertEquals(1, ChannelRules.quality("Prvi kanal HD"))
        assertEquals(2, ChannelRules.quality("XX: Prvi kanal FHD"))
        assertEquals(2, ChannelRules.quality("Prvi kanal Full HD"))
        assertEquals(3, ChannelRules.quality("Prvi kanal 4K"))
        assertEquals(3, ChannelRules.quality("Prvi kanal UHD HEVC"))
        // Slova „HD” u sredini reči nisu oznaka.
        assertEquals(0, ChannelRules.quality("Shdow kanal"))
    }

    @Test
    fun `base name drops quality tags and punctuation`() {
        assertEquals(ChannelRules.baseName("XX: Prvi kanal"), ChannelRules.baseName("XX | Prvi kanal FHD (H.265)"))
    }

    @Test
    fun `plus one needs its own tag`() {
        assertTrue(ChannelRules.isPlusOne("Prvi kanal +1"))
        assertTrue(ChannelRules.isPlusOne("Prvi kanal +1 HD"))
        assertFalse(ChannelRules.isPlusOne("Kanal+ 1"))
        assertFalse(ChannelRules.isPlusOne("Kanal +18"))
    }

    @Test
    fun `nothing is hidden while rules are off`() {
        val rows = listOf(Row(1, 1, "Prvi kanal"), Row(2, 1, "Prvi kanal HD"), Row(3, 1, "Prvi kanal +1"))
        assertEquals(emptySet<Long>(), ChannelRules.hidden(rows, ChannelRuleSettings()))
    }

    @Test
    fun `sd is hidden only when a better one exists in the same playlist`() {
        val rows = listOf(
            Row(1, 1, "Prvi kanal"),
            Row(2, 1, "Prvi kanal HD"),
            Row(3, 1, "Drugi kanal SD"),
            Row(4, 2, "Drugi kanal HD"),
            Row(5, 1, "Treci kanal SD"),
            Row(6, 1, "Treci kanal 4K"),
            Row(7, 1, "Treci kanal HD"),
        )
        assertEquals(setOf(1L, 5L), ChannelRules.hidden(rows, ChannelRuleSettings(hideSd = true)))
    }

    @Test
    fun `plus one channels are hidden`() {
        val rows = listOf(Row(1, 1, "Prvi kanal"), Row(2, 1, "Prvi kanal +1"))
        assertEquals(setOf(2L), ChannelRules.hidden(rows, ChannelRuleSettings(hidePlusOne = true)))
    }

    @Test
    fun `same name is shown once per playlist`() {
        val rows = listOf(Row(1, 1, "Prvi kanal"), Row(2, 1, "prvi kanal "), Row(3, 2, "Prvi kanal"), Row(4, 1, "Prvi kanal HD"))
        assertEquals(setOf(2L), ChannelRules.hidden(rows, ChannelRuleSettings(mergeSameName = true)))
    }

    @Test
    fun `favorites are never hidden`() {
        val rows = listOf(
            Row(1, 1, "Prvi kanal", kept = true),
            Row(2, 1, "Prvi kanal HD"),
            Row(3, 1, "Prvi kanal +1", kept = true),
            Row(4, 1, "Prvi kanal HD", kept = true),
        )
        val all = ChannelRuleSettings(hideSd = true, hidePlusOne = true, mergeSameName = true)
        assertEquals(emptySet<Long>(), ChannelRules.hidden(rows, all))
    }
}
