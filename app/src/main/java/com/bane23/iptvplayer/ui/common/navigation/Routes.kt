package com.bane23.iptvplayer.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Odredišta u aplikaciji. Zajednička su za telefon i TV, razlikuju se samo ekrani. */

/** Početni ekran: sekcije (Uživo, Filmovi, Serije...) sa donjom trakom na telefonu, bočnim menijem na TV-u. */
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

/** Detalji filma. Naziv, poster i nastavak fajla dolaze iz spiska, da ekran ima šta da prikaže odmah. */
@Serializable
data class MovieDetails(val movieId: Long, val name: String, val poster: String?, val extension: String?) : NavKey

/** Detalji serije sa sezonama i epizodama. */
@Serializable
data class SeriesDetails(val seriesId: Long, val name: String, val poster: String?) : NavKey

/** Plejer za film. [startOver] zanemaruje zapamćenu poziciju. */
@Serializable
data class PlayMovie(val movieId: Long, val name: String, val extension: String, val startOver: Boolean) : NavKey

/** Plejer za epizodu serije. */
@Serializable
data class PlayEpisode(val seriesId: Long, val episodeId: Long, val startOver: Boolean) : NavKey

/** Dodavanje liste sa telefona preko QR koda. Koristi se na TV-u. */
@Serializable
data object PairPlaylist : NavKey

/** Slanje svih lista i podešavanja na TV. [target] je adresa TV-a iz linka sa njegove stranice. */
@Serializable
data class SendToTv(val target: String) : NavKey

/** Pretraga kanala kroz celu aktivnu listu. */
@Serializable
data object Search : NavKey

/** Sakrivanje kategorija i promena njihovog redosleda. */
@Serializable
data object ManageCategories : NavKey
