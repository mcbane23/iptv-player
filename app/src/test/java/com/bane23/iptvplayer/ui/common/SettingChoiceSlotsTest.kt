package com.bane23.iptvplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingChoiceSlotsTest {

    private val options = listOf("Not set", "Alpha", "Beta", "Gamma")

    private fun slot(selected: Int) = SettingChoice("Slot", "", options, selected)

    private fun parent(vararg selected: Int) =
        SettingChoice("Languages", "", emptyList(), 0, slots = selected.map(::slot))

    @Test
    fun `value lists the chosen slots in order`() {
        assertEquals("Beta, Alpha, Gamma", parent(2, 1, 3).value)
    }

    @Test
    fun `empty and repeated slots are left out`() {
        assertEquals("Gamma, Alpha", parent(0, 3, 1).value)
        assertEquals("Beta", parent(2, 2, 0).value)
    }

    @Test
    fun `no chosen slot shows the not set label`() {
        assertEquals("Not set", parent(0, 0, 0).value)
    }

    @Test
    fun `a plain choice keeps its own value`() {
        assertEquals("Beta", slot(2).value)
    }
}
