package com.bane23.iptvplayer.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query(
        "SELECT cat.*, (SELECT COUNT(*) FROM channels ch WHERE ch.playlistId = cat.playlistId " +
            "AND ch.categoryId = cat.id) AS channelCount " +
            "FROM categories cat WHERE cat.playlistId = :playlistId AND cat.type = 'LIVE' ORDER BY cat.sortOrder",
    )
    fun observeLiveWithCount(playlistId: Long): Flow<List<CategoryWithCount>>

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
        "SELECT * FROM channels WHERE playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY sortOrder",
    )
    fun pagingSource(playlistId: Long, categoryId: Long?): PagingSource<Int, ChannelEntity>

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
