package com.bane23.iptvplayer.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Odredišta u aplikaciji. Zajednička su za telefon i TV, razlikuju se samo ekrani. */

/** Spisak lista (početni ekran dok ne stigne Live TV u Fazi 2). */
@Serializable
data object Playlists : NavKey

/** Dodavanje nove liste ([playlistId] je null) ili izmena postojeće. */
@Serializable
data class EditPlaylist(val playlistId: Long? = null) : NavKey
