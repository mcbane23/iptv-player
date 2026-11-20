package com.bane23.iptvplayer.ui.common.vod

import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * ID-jevi omiljenih filmova ili serija aktivne liste, za zvezdicu na posteru i dugi OK na ekranima
 * van Filmova i Serija („Početna”, zajednička pretraga).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun vodFavoriteIds(vod: VodRepository, kind: VodKind): Flow<Set<Long>> =
    vod.activePlaylistId.flatMapLatest { playlistId ->
        if (playlistId == null) {
            flowOf(emptySet())
        } else {
            vod.observeFavorites(playlistId, kind).map { list -> list.mapTo(HashSet()) { it.id } }
        }
    }
