package com.bane23.iptvplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSlotsTest {

    @Test
    fun `an older single language becomes the first slot`() {
        assertEquals(listOf("sr"), PlayerSettings.parseLanguageSlots("sr"))
        assertEquals(emptyList<String>(), PlayerSettings.parseLanguageSlots(""))
        assertEquals(emptyList<String>(), PlayerSettings.parseLanguageSlots(null))
    }

    @Test
    fun `slots keep empty places and survive a round trip`() {
        val slots = listOf("sr", "", "en")
        assertEquals("sr,,en", PlayerSettings.formatLanguageSlots(slots))
        assertEquals(slots, PlayerSettings.parseLanguageSlots("sr,,en"))
        assertEquals(listOf("sr"), PlayerSettings.parseLanguageSlots("sr,,"))
        assertEquals(listOf("a", "b", "c"), PlayerSettings.parseLanguageSlots("a,b,c,d"))
    }

    @Test
    fun `setting a slot leaves the others alone`() {
        assertEquals(listOf("", "", "en"), PlayerSettings.withLanguageSlot(emptyList(), 2, "en"))
        assertEquals(listOf("sr", "hr"), PlayerSettings.withLanguageSlot(listOf("sr"), 1, "hr"))
        assertEquals(listOf("", "hr"), PlayerSettings.withLanguageSlot(listOf("sr", "hr"), 0, ""))
        assertEquals(emptyList<String>(), PlayerSettings.withLanguageSlot(listOf("sr"), 0, ""))
        assertEquals(listOf("sr"), PlayerSettings.withLanguageSlot(listOf("sr"), 3, "en"))
    }

    @Test
    fun `priority skips empty places and repeats`() {
        val settings = PlayerSettings(subtitleLanguageSlots = listOf("", "hr", "hr"), audioLanguageSlots = listOf("sr", "", "en"))
        assertEquals(listOf("hr"), settings.subtitleLanguages)
        assertEquals(listOf("sr", "en"), settings.audioLanguages)
        assertEquals(emptyList<String>(), PlayerSettings().subtitleLanguages)
    }
}
