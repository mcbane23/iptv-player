package com.bane23.iptvplayer.ui.common.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodSearchTest {

    @Test
    fun ignoresCaseAndDiacritics() {
        assertEquals("cacak suma zivot", VodSearch.normalize("  Čačak ŠUMA Život "))
    }

    @Test
    fun djMatchesBothSpellings() {
        assertEquals("djurdjevdan", VodSearch.normalize("Đurđevdan"))
        assertTrue(VodSearch.normalize("đur") in VodSearch.normalize("Đurđevdan"))
        assertTrue(VodSearch.normalize("djur") in VodSearch.normalize("Đurđevdan"))
    }
}
