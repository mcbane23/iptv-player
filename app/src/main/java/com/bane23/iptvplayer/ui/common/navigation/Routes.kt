package com.bane23.iptvplayer.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Odredišta u aplikaciji. Zajednička su za telefon i TV, razlikuju se samo ekrani. */

/** Početni ekran: kategorije (i na TV-u kanali) aktivne liste. */
@Serializable
data object LiveTv : NavKey

/** Kanali jedne kategorije; [categoryId] null znači svi kanali liste. Koristi se na telefonu. */
@Serializable
data class Channels(val playlistId: Long, val categoryId: Long?, val title: String) : NavKey

/** Plejer. [categoryId] određuje kroz koje kanale se lista (null = svi kanali liste). */
@Serializable
data class Player(val channelId: Long, val categoryId: Long?) : NavKey

/** Spisak lista. */
@Serializable
data object Playlists : NavKey

/** Dodavanje nove liste ([playlistId] je null) ili izmena postojeće. */
@Serializable
data class EditPlaylist(val playlistId: Long? = null) : NavKey

/** TV vodič u obliku mreže za kanale jedne kategorije ([categoryId] null = svi kanali liste). */
@Serializable
data class Guide(val playlistId: Long, val categoryId: Long?) : NavKey

/** Pretraga kanala kroz celu aktivnu listu. */
@Serializable
data object Search : NavKey

/** Sakrivanje kategorija i promena njihovog redosleda. */
@Serializable
data object ManageCategories : NavKey

/** Podešavanja aplikacije. */
@Serializable
data object Settings : NavKey
