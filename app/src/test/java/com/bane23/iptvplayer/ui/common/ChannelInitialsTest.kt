package com.bane23.iptvplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelInitialsTest {

    @Test
    fun shortAbbreviationKeepsNumber() {
        assertEquals("ABC1", channelInitials("ABC 1 HD"))
        assertEquals("XYZ", channelInitials("XYZ"))
    }

    @Test
    fun twoWordsGiveTwoLetters() {
        assertEquals("PK", channelInitials("Prvi Kanal"))
        assertEquals("ZD", channelInitials("zvezdani dokumentarci FHD"))
    }

    @Test
    fun wordAndNumber() {
        assertEquals("S2", channelInitials("Sportski 2"))
    }

    @Test
    fun singleLongWord() {
        assertEquals("FI", channelInitials("Filmovi"))
    }

    @Test
    fun countryPrefixIsSkipped() {
        assertEquals("PK", channelInitials("XX: Prvi Kanal"))
        assertEquals("PK", channelInitials("|XX| Prvi Kanal"))
        assertEquals("PK", channelInitials("[XX] Prvi Kanal HD"))
    }

    @Test
    fun atMostFourCharacters() {
        assertEquals("ABCD", channelInitials("ABCD 123"))
    }

    @Test
    fun emptyName() {
        assertEquals("", channelInitials(""))
        assertEquals("", channelInitials("HD"))
    }
}
