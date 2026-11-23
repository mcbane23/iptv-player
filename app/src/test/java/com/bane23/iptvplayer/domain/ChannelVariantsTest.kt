package com.bane23.iptvplayer.domain

import com.bane23.iptvplayer.domain.ChannelVariants.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelVariantsTest {

    @Test
    fun `same channel in another quality gets the same key`() {
        val key = ChannelRules.variantKey("RS: Sport Uno HD")
        assertEquals("RS|SPORT UNO", key)
        assertEquals(key, ChannelRules.variantKey("SR| Sport Uno VIP RAW"))
        assertEquals(key, ChannelRules.variantKey("[RS] Sport Uno 4K HEVC"))
        assertEquals(key, ChannelRules.variantKey("Sport Uno FHD", category = "RS | Sportski"))
        assertEquals(key, ChannelRules.variantKey("RS Sport Uno", category = "SR| SPORT VIP RAW"))
    }

    @Test
    fun `different channel or country gets a different key`() {
        val key = ChannelRules.variantKey("RS: Sport Uno HD")
        assertNotEquals(key, ChannelRules.variantKey("RS: Sport Uno 2 HD"))
        assertNotEquals(key, ChannelRules.variantKey("DE: Sport Uno HD"))
        assertNotEquals(key, ChannelRules.variantKey("Sport Uno HD"))
        assertNotEquals(key, ChannelRules.variantKey("RS: Sport Uno Premium HD"))
    }

    @Test
    fun `key keeps a prefix that is not a country and never ends up empty`() {
        assertEquals("|PAKET FILM JEDAN", ChannelRules.variantKey("PAKET: Film Jedan HD"))
        assertEquals("|HD", ChannelRules.variantKey("HD"))
    }

    @Test
    fun `picks the 720p version when the chosen one is higher`() {
        val options = listOf(Option(1, 1080), Option(2, 720), Option(3, 576))
        assertEquals(2L, ChannelVariants.pick(options, 1))
    }

    @Test
    fun `keeps the chosen version when it is 720p or lower`() {
        val options = listOf(Option(1, 720), Option(2, 696), Option(3, 576))
        assertEquals(1L, ChannelVariants.pick(options, 1))
        assertEquals(3L, ChannelVariants.pick(options, 3))
    }

    @Test
    fun `among 720p versions takes the one with the lowest known bitrate`() {
        val options = listOf(Option(1, 2160), Option(2, 720, 0), Option(3, 720, 3_000_000), Option(4, 720, 2_200_000))
        assertEquals(4L, ChannelVariants.pick(options, 1))
    }

    @Test
    fun `without a 720p version takes the lowest known one below the chosen`() {
        assertEquals(2L, ChannelVariants.pick(listOf(Option(1, 2160), Option(2, 1080), Option(3, 576)), 1))
        // Ništa niže od izabrane, osim SD: ostaje izabrana.
        assertEquals(1L, ChannelVariants.pick(listOf(Option(1, 1080), Option(2, 2160), Option(3, 576)), 1))
    }

    @Test
    fun `unknown chosen version switches only to a known 720p one`() {
        assertEquals(2L, ChannelVariants.pick(listOf(Option(1), Option(2, 720)), 1))
        assertEquals(1L, ChannelVariants.pick(listOf(Option(1), Option(2, 1080)), 1))
        assertEquals(1L, ChannelVariants.pick(listOf(Option(1), Option(2)), 1))
        assertEquals(9L, ChannelVariants.pick(listOf(Option(1, 720)), 9))
    }
}
