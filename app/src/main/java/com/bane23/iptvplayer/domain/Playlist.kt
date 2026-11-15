package com.bane23.iptvplayer.domain

enum class PlaylistType { M3U_URL, M3U_FILE, XTREAM }

enum class CategoryType { LIVE, VOD, SERIES }

/** Lista onako kako je vidi UI sloj. Lozinka se nikad ne izlaže UI-ju. */
data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    /** M3U link ili URI fajla; za Xtream adresa servera. */
    val source: String,
    val username: String?,
    val userAgent: String?,
    /** EPG link koji je uneo korisnik, ili null ako se koristi onaj iz liste. */
    val epgUrl: String? = null,
    val epgShiftHours: Int = 0,
    val channelCount: Int,
    val lastRefreshedAt: Long?,
    val isActive: Boolean,
)

/** Podaci koje korisnik unosi pri dodavanju ili izmeni liste. */
data class PlaylistDraft(
    val type: PlaylistType,
    val name: String,
    /** M3U link, URI fajla ili adresa Xtream servera. */
    val source: String,
    val username: String = "",
    /** Prazno pri izmeni znači: zadrži postojeću lozinku. */
    val password: String = "",
    /** Prazno = podrazumevani User-Agent. */
    val userAgent: String = "",
    /** Prazno = vodič iz same liste (M3U zaglavlje, odnosno Xtream nalog). */
    val epgUrl: String = "",
    /** Pomak vodiča u satima. */
    val epgShiftHours: Int = 0,
)
