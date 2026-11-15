package com.bane23.iptvplayer.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Kolona koja kaže da li je kanal među favoritima. */
private const val FAVORITE_COLUMN =
    "EXISTS(SELECT 1 FROM favorites f WHERE f.playlistId = ch.playlistId AND f.channelKey = ch.stableKey) AS favorite"

/** Kanali koji su u favoritima. Isti strim u više kategorija se računa jednom. */
private const val FAVORITE_CHANNELS =
    "FROM channels ch JOIN favorites fav ON fav.playlistId = ch.playlistId AND fav.channelKey = ch.stableKey " +
        "WHERE ch.playlistId = :playlistId GROUP BY ch.stableKey ORDER BY fav.addedAt, ch.sortOrder"

/** Nedavno gledani kanali, najskoriji prvi. */
private const val RECENT_CHANNELS =
    "FROM channels ch JOIN history h ON h.playlistId = ch.playlistId AND h.channelKey = ch.stableKey " +
        "WHERE ch.playlistId = :playlistId GROUP BY ch.stableKey ORDER BY h.watchedAt DESC, ch.sortOrder"

/** Redosled kategorija: prvo ručno raspoređene, pa ostale po redosledu iz liste. */
private const val CATEGORY_ORDER = "ORDER BY cat.userOrder IS NULL, cat.userOrder, cat.sortOrder"

private const val CATEGORY_COUNT =
    "(SELECT COUNT(*) FROM channels ch WHERE ch.playlistId = cat.playlistId AND ch.categoryId = cat.id) AS channelCount"

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM channels c WHERE c.playlistId = p.id) AS channelCount " +
            "FROM playlists p ORDER BY p.name COLLATE NOCASE",
    )
    fun observeAllWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT id FROM playlists ORDER BY name COLLATE NOCASE LIMIT 1")
    suspend fun firstId(): Long?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET lastRefreshedAt = :time, epgUrl = :epgUrl WHERE id = :id")
    suspend fun markRefreshed(id: Long, time: Long, epgUrl: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    /** Vidljive kategorije, redom kojim ih korisnik vidi. */
    @Query(
        "SELECT cat.*, $CATEGORY_COUNT FROM categories cat " +
            "WHERE cat.playlistId = :playlistId AND cat.type = 'LIVE' AND cat.hidden = 0 $CATEGORY_ORDER",
    )
    fun observeLiveWithCount(playlistId: Long): Flow<List<CategoryWithCount>>

    /** Sve kategorije, i sakrivene (za ekran uređivanja). */
    @Query(
        "SELECT cat.*, $CATEGORY_COUNT FROM categories cat " +
            "WHERE cat.playlistId = :playlistId AND cat.type = 'LIVE' $CATEGORY_ORDER",
    )
    fun observeAllLiveWithCount(playlistId: Long): Flow<List<CategoryWithCount>>

    @Query("SELECT cat.* FROM categories cat WHERE cat.playlistId = :playlistId AND cat.type = 'LIVE' $CATEGORY_ORDER")
    suspend fun getLive(playlistId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId")
    suspend fun getByPlaylist(playlistId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE categories SET userOrder = :order WHERE id = :id")
    suspend fun setUserOrder(id: Long, order: Int)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface ChannelDao {
    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    fun observeCountByPlaylist(playlistId: Long): Flow<Int>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    /** Kanali jedne kategorije, ili svi kanali liste kad je [categoryId] null. */
    @Query(
        "SELECT ch.*, $FAVORITE_COLUMN FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) ORDER BY ch.sortOrder",
    )
    fun pagingSource(playlistId: Long, categoryId: Long?): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.*, 1 AS favorite $FAVORITE_CHANNELS")
    fun favoritesPagingSource(playlistId: Long): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.*, $FAVORITE_COLUMN $RECENT_CHANNELS")
    fun recentPagingSource(playlistId: Long): PagingSource<Int, ChannelWithFavorite>

    /** Pretraga po nazivu kroz sve kategorije. U [pattern] su `%`, `_` i `\` već zaštićeni. */
    @Query(
        "SELECT ch.*, $FAVORITE_COLUMN FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND ch.name LIKE :pattern ESCAPE '\\' ORDER BY ch.name COLLATE NOCASE, ch.sortOrder",
    )
    fun searchPagingSource(playlistId: Long, pattern: String): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.id $FAVORITE_CHANNELS")
    suspend fun favoriteIds(playlistId: Long): List<Long>

    @Query("SELECT ch.id $RECENT_CHANNELS")
    suspend fun recentIds(playlistId: Long): List<Long>

    @Query(
        "SELECT COUNT(DISTINCT ch.stableKey) FROM channels ch JOIN favorites fav " +
            "ON fav.playlistId = ch.playlistId AND fav.channelKey = ch.stableKey WHERE ch.playlistId = :playlistId",
    )
    fun observeFavoriteCount(playlistId: Long): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT ch.stableKey) FROM channels ch JOIN history h " +
            "ON h.playlistId = ch.playlistId AND h.channelKey = ch.stableKey WHERE ch.playlistId = :playlistId",
    )
    fun observeRecentCount(playlistId: Long): Flow<Int>

    /** Redni broj kanala (od nule) unutar kategorije, odnosno cele liste. */
    @Query(
        "SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) AND sortOrder < :sortOrder",
    )
    suspend fun positionOf(playlistId: Long, categoryId: Long?, sortOrder: Int): Int

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) AND sortOrder > :sortOrder " +
            "ORDER BY sortOrder LIMIT 1",
    )
    suspend fun next(playlistId: Long, categoryId: Long?, sortOrder: Int): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) AND sortOrder < :sortOrder " +
            "ORDER BY sortOrder DESC LIMIT 1",
    )
    suspend fun previous(playlistId: Long, categoryId: Long?, sortOrder: Int): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY sortOrder LIMIT 1",
    )
    suspend fun first(playlistId: Long, categoryId: Long?): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY sortOrder DESC LIMIT 1",
    )
    suspend fun last(playlistId: Long, categoryId: Long?): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number ORDER BY sortOrder LIMIT 1")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND channelKey = :channelKey")
    suspend fun delete(playlistId: Long, channelKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND channelKey = :channelKey)")
    suspend fun exists(playlistId: Long, channelKey: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND channelKey = :channelKey)")
    fun observeExists(playlistId: Long, channelKey: String): Flow<Boolean>
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity)

    /** Zadržava samo [keep] najskorije gledanih kanala liste. */
    @Query(
        "DELETE FROM history WHERE playlistId = :playlistId AND channelKey NOT IN " +
            "(SELECT channelKey FROM history WHERE playlistId = :playlistId ORDER BY watchedAt DESC LIMIT :keep)",
    )
    suspend fun trim(playlistId: Long, keep: Int)

    @Query("DELETE FROM history WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)
}
