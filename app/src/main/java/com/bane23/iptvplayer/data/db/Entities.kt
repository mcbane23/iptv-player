package com.bane23.iptvplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bane23.iptvplayer.domain.CategoryType
import com.bane23.iptvplayer.domain.PlaylistType

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** M3U link, URI fajla ili adresa Xtream servera. */
    val source: String,
    val username: String? = null,
    /** Xtream lozinka, šifrovana preko Android Keystore-a. Nikad običan tekst. */
    val passwordEncrypted: String? = null,
    val epgUrl: String? = null,
    val lastRefreshedAt: Long? = null,
    /** User-Agent koji se šalje serveru ove liste; null = podrazumevani. */
    val userAgent: String? = null,
    /** EPG link koji je uneo korisnik; ima prednost nad [epgUrl] iz same liste. */
    val epgUrlOverride: String? = null,
    /** Pomak vodiča u satima (kad provajder šalje vremena u pogrešnoj zoni). */
    val epgShiftHours: Int = 0,
    val epgRefreshedAt: Long? = null,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val type: CategoryType,
    val sortOrder: Int,
    /** Korisnik je sakrio kategoriju; čuva se i posle osvežavanja liste (po nazivu). */
    val hidden: Boolean = false,
    /** Redosled koji je zadao korisnik; null = kategorija još nije ručno raspoređena. */
    val userOrder: Int? = null,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId", "categoryId", "sortOrder"),
        Index("playlistId", "sortOrder"),
        Index("playlistId", "name"),
        Index("playlistId", "number"),
        Index("playlistId", "stableKey"),
        Index("playlistId", "epgKey"),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long?,
    val name: String,
    val logo: String? = null,
    /** Pun link strima za M3U. Za Xtream je null: link se sastavlja pri puštanju iz [xtreamStreamId]. */
    val streamUrl: String? = null,
    val xtreamStreamId: Long? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val number: Int? = null,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val sortOrder: Int,
    /**
     * Ključ kanala koji ostaje isti i posle osvežavanja liste (kad se menjaju [id]-jevi):
     * link strima za M3U, odnosno Xtream ID. Favoriti i istorija se vezuju za njega.
     */
    val stableKey: String,
    /**
     * ID kanala u vodiču (malim slovima). Pri uvozu liste to je [tvgId]; kanalima bez njega
     * se dodeljuje pri učitavanju vodiča, ako se naziv poklopi sa nazivom kanala u vodiču.
     */
    val epgKey: String? = null,
)

/** Emisija iz TV vodiča. Vremena su u milisekundama (UTC), već sa pomakom liste. */
@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "channelKey", "startMs"), Index("stopMs")],
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelKey: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String? = null,
    /** Oznaka učitavanja vodiča kojem red pripada; posle uspešnog učitavanja stari redovi se brišu. */
    val generation: Long,
)

/** Dokle je korisnik stigao sa filmom ili epizodom, za nastavak gledanja. */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["playlistId", "kind", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "seriesId", "updatedAt")],
)
data class WatchProgressEntity(
    val playlistId: Long,
    /** "movie" ili "episode". */
    val kind: String,
    /** Xtream ID filma, odnosno epizode. */
    val itemId: Long,
    /** Serija kojoj epizoda pripada; 0 za film. */
    val seriesId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Naziv filma, odnosno serije, za red „Nastavi gledanje” (od verzije baze 6). */
    val title: String? = null,
    /** Poster filma, odnosno serije. */
    val poster: String? = null,
    /** Nastavak fajla filma (mp4, mkv...); za epizode null. */
    val extension: String? = null,
    /**
     * Kad je film, odnosno epizoda, označen kao odgledan: sam pri gledanju do kraja ili ručno.
     * null znači da nije odgledan (od verzije baze 8).
     */
    val watchedAt: Long? = null,
)

/** Ono što je o kanalu potrebno za povezivanje sa vodičem. */
data class ChannelEpgRow(val id: Long, val name: String, val tvgId: String?, val catchupType: String? = null)

/** Kanal koji je korisnik označio kao omiljen. */
@Entity(
    tableName = "favorites",
    primaryKeys = ["playlistId", "channelKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    val playlistId: Long,
    val channelKey: String,
    val addedAt: Long,
)

/** Kanal koji je gledan; čuva se samo vreme poslednjeg gledanja. */
@Entity(
    tableName = "history",
    primaryKeys = ["playlistId", "channelKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HistoryEntity(
    val playlistId: Long,
    val channelKey: String,
    val watchedAt: Long,
)

data class ChannelWithFavorite(
    @Embedded val channel: ChannelEntity,
    @ColumnInfo(name = "favorite") val favorite: Boolean,
    /** Naslov emisije koja je trenutno na programu, ako vodič postoji. */
    @ColumnInfo(name = "nowTitle") val nowTitle: String? = null,
    @ColumnInfo(name = "nowStartMs") val nowStartMs: Long? = null,
    @ColumnInfo(name = "nowStopMs") val nowStopMs: Long? = null,
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "channelCount") val channelCount: Int,
)

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "channelCount") val channelCount: Int,
)

/** Omiljeni film ili serija. Naziv i poster se čuvaju da bi se spisak prikazao bez servera. */
@Entity(
    tableName = "vod_favorites",
    primaryKeys = ["playlistId", "kind", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "kind", "addedAt")],
)
data class VodFavoriteEntity(
    val playlistId: Long,
    /** "movie" ili "series". */
    val kind: String,
    /** Xtream ID filma, odnosno serije. */
    val itemId: Long,
    val name: String,
    val poster: String?,
    /** Nastavak fajla filma; za serije null. */
    val extension: String?,
    val addedAt: Long,
)

/**
 * Ono što je na Trakt nalogu označeno kao odgledano, preuzeto pri poslednjoj sinhronizaciji.
 * Ključ je TMDB ID: filma, odnosno serije, uz broj sezone i epizode (0 i 0 za film).
 * Ne zavisi od liste, jer je Trakt nalog jedan za sve liste.
 */
@Entity(tableName = "trakt_watched", primaryKeys = ["kind", "tmdbId", "season", "episode"])
data class TraktWatchedEntity(
    /** "movie" ili "episode". */
    val kind: String,
    val tmdbId: Long,
    val season: Int,
    val episode: Int,
    val watchedAt: Long,
)

/**
 * Promena oznake „odgledano” koja još nije poslata na Trakt. Čuva se ID sa liste; TMDB ID se
 * pronalazi pri slanju.
 */
@Entity(tableName = "trakt_pending")
data class TraktPendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** "movie" ili "episode". */
    val kind: String,
    val itemId: Long,
    /** Serija kojoj epizoda pripada; 0 za film. */
    val seriesId: Long,
    val watched: Boolean,
    val createdAt: Long,
)

/**
 * Pozicija gledanja koja još nije poslata na Trakt (za nastavak na drugom uređaju). Za svaku
 * stavku čuva se samo poslednja.
 */
@Entity(tableName = "trakt_playback_pending", primaryKeys = ["playlistId", "kind", "itemId"])
data class TraktPlaybackPendingEntity(
    val playlistId: Long,
    /** "movie" ili "episode". */
    val kind: String,
    val itemId: Long,
    /** Serija kojoj epizoda pripada; 0 za film. */
    val seriesId: Long,
    /** Odgledani deo u procentima (0 do 100). */
    val progress: Float,
    val createdAt: Long,
)

/** Snimak kanala: fajl na uređaju i podaci za ekran „Snimci”. Ne zavisi od liste, pa ostaje i kad se lista obriše. */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Lista i stalni ključ kanala (kao kod favorita): po njima se kanal nalazi kad zakazano snimanje krene. */
    val playlistId: Long,
    val channelKey: String,
    val channelName: String,
    /** Emisija koja je išla kad je snimanje krenulo, ili naziv kanala. */
    val title: String,
    /** Pun put fajla sa snimkom. */
    val path: String,
    val startedAt: Long,
    /** Kraj snimanja; null dok se snima. */
    val endedAt: Long? = null,
    /** Kad snimanje samo staje (zakazano); null = dok ga korisnik ne zaustavi. */
    val stopAtMs: Long? = null,
    val sizeBytes: Long = 0,
    /** [STATUS_SCHEDULED], [STATUS_RECORDING], [STATUS_DONE] ili [STATUS_FAILED]. */
    val status: String,
    /** Zašto je snimanje stalo pre vremena ili nije ni krenulo ([ERROR_STREAM], [ERROR_SPACE], ...), ili null. */
    val error: String? = null,
) {
    companion object {
        /** Zakazano: [startedAt] je vreme kad treba da krene, a [stopAtMs] kad staje. */
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_RECORDING = "recording"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val ERROR_STREAM = "stream"
        const val ERROR_SPACE = "space"
        const val ERROR_UNSUPPORTED = "unsupported"

        /** Uređaj je bio ugašen ili aplikacija nije smela da se pokrene u zakazano vreme. */
        const val ERROR_MISSED = "missed"

        /** Kanala više nema u listi. */
        const val ERROR_NO_CHANNEL = "channel"
    }
}
