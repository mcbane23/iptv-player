package com.bane23.iptvplayer.ui.common

import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.domain.FavoriteSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteListGroupsTest {

    private val sport = FavoriteList(id = 1, name = "Sport", channelCount = 3)
    private val kids = FavoriteList(id = 7, name = "Deca", channelCount = 0)
    private val categories = listOf(Category(id = 5, name = "Vesti", channelCount = 2))

    @Test
    fun `list group id is virtual and maps back to the list`() {
        assertTrue(ChannelGroups.isVirtual(sport.groupId))
        assertEquals(1L, ChannelGroups.favoriteListId(sport.groupId))
        assertEquals(7L, ChannelGroups.favoriteListId(kids.groupId))
    }

    @Test
    fun `built in groups and categories are not lists`() {
        assertNull(ChannelGroups.favoriteListId(ChannelGroups.FAVORITES))
        assertNull(ChannelGroups.favoriteListId(ChannelGroups.RECENT))
        assertNull(ChannelGroups.favoriteListId(ChannelGroups.ALL))
        assertNull(ChannelGroups.favoriteListId(5L))
        assertNull(ChannelGroups.favoriteListId(null))
    }

    @Test
    fun `a list remembered by an older version opens favorites`() {
        assertEquals(ChannelGroups.FAVORITES, LastLiveCategory.resolve("l:7", categories, hasRecent = false))
        assertEquals("fav", LastLiveCategory.token(ChannelGroups.FAVORITES, categories))
    }

    @Test
    fun `sections are not separate groups on the live screen`() {
        val state = LiveTvUiState(categories = categories, favoriteSections = FavoriteSections(2, listOf(sport)))
        assertTrue(state.hasGroup(ChannelGroups.FAVORITES))
        assertEquals(false, state.hasGroup(sport.groupId))
        assertEquals(listOf(sport), state.favoriteLists)
    }

    @Test
    fun `section starts follow unsorted channels and skip empty sections`() {
        val f1 = FavoriteList(id = 3, name = "Formula 1", channelCount = 2)
        val sections = FavoriteSections(unsorted = 2, sections = listOf(sport, kids, f1))
        assertEquals(2, sections.start(sport.id))
        assertNull(sections.start(kids.id))
        assertEquals(5, sections.start(f1.id))
        assertNull(sections.start(99))
        assertEquals(listOf(0, 2, 5), sections.starts())
    }

    @Test
    fun `without unsorted channels the first section starts at the top`() {
        val sections = FavoriteSections(unsorted = 0, sections = listOf(kids, sport))
        assertEquals(0, sections.start(sport.id))
        assertEquals(listOf(0), sections.starts())
    }

    @Test
    fun `channel position maps to its section`() {
        val f1 = FavoriteList(id = 3, name = "Formula 1", channelCount = 2)
        val sections = FavoriteSections(unsorted = 1, sections = listOf(sport, kids, f1))
        assertNull(sections.sectionAt(0))
        assertEquals(sport, sections.sectionAt(1))
        assertEquals(sport, sections.sectionAt(3))
        assertEquals(f1, sections.sectionAt(4))
        assertNull(sections.sectionAt(6))
    }

    @Test
    fun `channel up and down step between section starts`() {
        val starts = listOf(0, 2, 5)
        assertEquals(2, com.bane23.iptvplayer.ui.tv.sectionStep(starts, 0, forward = true))
        assertEquals(5, com.bane23.iptvplayer.ui.tv.sectionStep(starts, 3, forward = true))
        assertNull(com.bane23.iptvplayer.ui.tv.sectionStep(starts, 6, forward = true))
        assertEquals(2, com.bane23.iptvplayer.ui.tv.sectionStep(starts, 3, forward = false))
        assertEquals(0, com.bane23.iptvplayer.ui.tv.sectionStep(starts, 2, forward = false))
        assertNull(com.bane23.iptvplayer.ui.tv.sectionStep(starts, 0, forward = false))
    }
}
