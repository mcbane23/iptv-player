package com.bane23.iptvplayer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    @Test
    fun `only four digits are a valid pin`() {
        assertTrue(Pin.isValid("0420"))
        assertFalse(Pin.isValid("123"))
        assertFalse(Pin.isValid("12345"))
        assertFalse(Pin.isValid("12a4"))
        assertFalse(Pin.isValid(""))
    }

    @Test
    fun `stored record matches only the right pin`() {
        val stored = Pin.hash("0420")
        assertTrue(Pin.matches("0420", stored))
        assertFalse(Pin.matches("0421", stored))
        assertFalse(Pin.matches("", stored))
        // Stalan dodatak: nasumičan zapis ponekad slučajno sadrži te četiri cifre, pa bi test povremeno pao.
        assertFalse(Pin.hash("0420", "abc").substringAfter(':').contains("0420"))
    }

    @Test
    fun `same pin gives a different record each time but the same one for the same salt`() {
        assertNotEquals(Pin.hash("0420"), Pin.hash("0420"))
        assertEquals(Pin.hash("0420", "abc"), Pin.hash("0420", "abc"))
    }

    @Test
    fun `broken or missing record never matches`() {
        assertFalse(Pin.matches("0420", null))
        assertFalse(Pin.matches("0420", ""))
        assertFalse(Pin.matches("0420", "bez-dvotacke"))
        assertFalse(Pin.matches("0420", ":"))
    }
}
