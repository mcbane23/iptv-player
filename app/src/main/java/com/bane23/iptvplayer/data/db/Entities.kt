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
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "channelCount") val channelCount: Int,
)

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "channelCount") val channelCount: Int,
)
