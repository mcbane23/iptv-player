package com.bane23.iptvplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingChoiceStepTest {

    private fun choice(selected: Int, picked: MutableList<Int>) = SettingChoice(
        title = "Brzina",
        summary = "",
        options = listOf("1×", "2×", "4×"),
        selected = selected,
        onSelect = { picked.add(it) },
    )

    @Test
    fun `korak ide na susednu vrednost`() {
        val picked = mutableListOf<Int>()
        choice(1, picked).step(1)
        choice(1, picked).step(-1)
        assertEquals(listOf(2, 0), picked)
    }

    @Test
    fun `na kraju spiska korak ne radi nista i ne ide ukrug`() {
        val picked = mutableListOf<Int>()
        choice(2, picked).step(1)
        choice(0, picked).step(-1)
        assertEquals(emptyList<Int>(), picked)
    }
}
