package com.bane23.iptvplayer.data.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {

    @Test
    fun `redact removes addresses with login data`() {
        val text = "Unable to connect to http://primer.invalid:8080/live/korisnik/lozinka/15.ts (timeout)"
        val clean = CrashLog.redact(text)
        assertEquals("Unable to connect to [url] (timeout)", clean)
    }

    @Test
    fun `redact keeps text without addresses`() {
        val text = "java.lang.IllegalStateException: index 3, size 2\n\tat com.example.Klasa.metoda(Klasa.kt:12)"
        assertEquals(text, CrashLog.redact(text))
    }

    @Test
    fun `format has device info thread and trace without addresses`() {
        val error = IllegalStateException("Greška za https://primer.invalid/get.php?username=a&password=b", RuntimeException("uzrok"))
        val text = CrashLog.format(0L, "main", error, listOf("Glowbox 1.0 (1, dev)", "Uređaj"))
        assertTrue(text.contains("Glowbox 1.0 (1, dev)"))
        assertTrue(text.contains("Thread: main"))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: uzrok"))
        assertFalse(text.contains("primer.invalid"))
        assertFalse(text.contains("password"))
    }
}
