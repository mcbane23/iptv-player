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
