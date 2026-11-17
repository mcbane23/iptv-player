package com.bane23.iptvplayer.ui.common.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    @Test
    fun `nepoznato ime daje podrazumevanu velicinu za uredjaj`() {
        assertEquals(UiScale.P75, UiScale.fromName(null, isTv = true))
        assertEquals(UiScale.P100, UiScale.fromName(null, isTv = false))
        assertEquals(UiScale.P75, UiScale.fromName("NEMA", isTv = true))
    }

    @Test
    fun `sacuvano ime se postuje`() {
        assertEquals(UiScale.P85, UiScale.fromName("P85", isTv = true))
        assertEquals(UiScale.P85, UiScale.fromName("P85", isTv = false))
    }

    @Test
    fun `najsitnija velicina ne vazi na telefonu`() {
        assertEquals(UiScale.P65, UiScale.fromName("P65", isTv = true))
        assertEquals(UiScale.P100, UiScale.fromName("P65", isTv = false))
    }

    @Test
    fun `sledeca velicina ide ukrug`() {
        assertEquals(UiScale.P65, UiScale.next(UiScale.P75, isTv = true))
        assertEquals(UiScale.P100, UiScale.next(UiScale.P65, isTv = true))
        assertEquals(UiScale.P100, UiScale.next(UiScale.P75, isTv = false))
    }

    @Test
    fun `naziv je procenat`() {
        assertEquals("75%", UiScale.P75.label)
        assertEquals("100%", UiScale.P100.label)
    }
}
