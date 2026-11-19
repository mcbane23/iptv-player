package com.bane23.iptvplayer.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Odredišta u aplikaciji. Zajednička su za telefon i TV, razlikuju se samo ekrani. */

/** Početni ekran: sekcije (Uživo, Filmovi, Serije...) sa donjom trakom na telefonu, bočnim menijem na TV-u. */
@Serializable
data object LiveTv : NavKey

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
data class PlayMovie(
    val movieId: Long,
    val name: String,
    val extension: String,
    val startOver: Boolean,
    val poster: String? = null,
) : NavKey

/** Plejer za epizodu serije. */
@Serializable
data class PlayEpisode(val seriesId: Long, val episodeId: Long, val startOver: Boolean) : NavKey

/** Pretraga filmova ([series] = false) ili serija kroz sve kategorije aktivne liste. */
@Serializable
data class VodSearch(val series: Boolean) : NavKey

/** Dodavanje liste sa telefona preko QR koda. Koristi se na TV-u. */
@Serializable
data object PairPlaylist : NavKey

/** Slanje svih lista i podešavanja na TV. [target] je adresa TV-a iz linka sa njegove stranice. */
@Serializable
data class SendToTv(val target: String) : NavKey

/** Prenos svih lista, omiljenih, podešavanja i naloga na TV koji se bira sa spiska. Koristi se na telefonu. */
@Serializable
data object TransferToTv : NavKey

/** Arhiva kanala: spisak emisija koje su prošle i mogu da se pogledaju. */
@Serializable
data class ChannelArchive(val channelId: Long) : NavKey

/** Plejer za emisiju iz arhive. [startToken] je početak po satu Xtream servera (za M3U null). */
@Serializable
data class PlayArchive(
    val channelId: Long,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val startToken: String?,
) : NavKey

/** Jedna pretraga za sve: kanali, emisije iz vodiča, filmovi i serije aktivne liste. Koristi se na TV-u. */
@Serializable
data object SearchAll : NavKey

/** Pretraga kanala kroz celu aktivnu listu. */
@Serializable
data object Search : NavKey

/**
 * Sakrivanje kategorija i promena njihovog redosleda. [section] je kartica koja se otvara prva
 * (ime vrednosti iz `CategorySection`).
 */
@Serializable
data class ManageCategories(val section: String = "LIVE") : NavKey

/** Trakt nalog: prijava i sinhronizacija odgledanog. */
@Serializable
data object TraktAccount : NavKey

/** OpenSubtitles nalog: prijava za veći dnevni broj preuzimanja titlova. */
@Serializable
data object OpenSubtitlesAccount : NavKey

/** Roditeljska zaštita: PIN i zaključane kategorije. */
@Serializable
data object ParentalControl : NavKey

/** Više kanala odjednom: 2 ili 4 polja, zvuk sa izabranog. */
@Serializable
data object MultiView : NavKey

/** Daljinski na telefonu dok se sadržaj pušta na TV-u preko Chromecast-a. */
@Serializable
data object CastRemote : NavKey

/** Plejer na TV-u za sadržaj poslat sa telefona kroz kućnu mrežu. */
@Serializable
data object CastPlayback : NavKey

/** Snimci kanala sačuvani na uređaju. */
@Serializable
data object Recordings : NavKey

/** Podešavanje šta koje dugme daljinskog radi u plejeru kanala (TV). */
@Serializable
data object RemoteButtons : NavKey

/** Filmovi i epizode preuzeti na telefon. */
@Serializable
data object Downloads : NavKey

/**
 * Plejer za preuzeti film ili epizodu. [series] i [lockId] (ID serije, odnosno filma) služe za
 * proveru zaključavanja pre puštanja.
 */
@Serializable
data class PlayDownload(val downloadId: Long, val series: Boolean, val lockId: Long) : NavKey

/** Plejer za snimak sa uređaja. */
@Serializable
data class PlayRecording(val recordingId: Long) : NavKey
