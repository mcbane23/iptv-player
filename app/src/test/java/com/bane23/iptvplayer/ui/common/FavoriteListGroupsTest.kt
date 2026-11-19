package com.bane23.iptvplayer.ui.common

import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.domain.FavoriteList
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
    fun `last opened list is remembered and found again`() {
        val token = LastLiveCategory.token(kids.groupId, categories)
        assertEquals(kids.groupId, LastLiveCategory.resolve(token, categories, hasRecent = false, lists = listOf(sport, kids)))
    }

    @Test
    fun `deleted list is not restored`() {
        val token = LastLiveCategory.token(kids.groupId, categories)
        assertNull(LastLiveCategory.resolve(token, categories, hasRecent = false, lists = listOf(sport)))
    }

    @Test
    fun `ui state knows its lists as groups`() {
        val state = LiveTvUiState(categories = categories, favoriteLists = listOf(sport))
        assertTrue(state.hasGroup(sport.groupId))
        assertEquals(false, state.hasGroup(kids.groupId))
        assertEquals(sport, state.favoriteList(sport.groupId))
        assertNull(state.favoriteList(5L))
    }

    @Test
    fun `favorites and its lists are one group on screen`() {
        val state = LiveTvUiState(categories = categories, favoriteLists = listOf(sport))
        assertTrue(state.isFavoritesGroup(ChannelGroups.FAVORITES))
        assertTrue(state.isFavoritesGroup(sport.groupId))
        assertEquals(false, state.isFavoritesGroup(kids.groupId))
        assertEquals(false, state.isFavoritesGroup(ChannelGroups.RECENT))
        assertEquals(false, state.isFavoritesGroup(ChannelGroups.ALL))
        assertEquals(false, state.isFavoritesGroup(5L))
        assertEquals(false, state.isFavoritesGroup(null))
    }
}
