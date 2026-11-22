package com.bane23.iptvplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        EpgProgramEntity::class,
        WatchProgressEntity::class,
        VodFavoriteEntity::class,
        TraktWatchedEntity::class,
        TraktPendingEntity::class,
        TraktPlaybackPendingEntity::class,
        RecordingEntity::class,
        EpgChannelEntity::class,
        EpgLinkEntity::class,
        FavoriteListEntity::class,
        FavoriteListItemEntity::class,
        DownloadEntity::class,
        HiddenChannelEntity::class,
        LockedItemEntity::class,
    ],
    version = 23,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun hiddenChannelDao(): HiddenChannelDao
    abstract fun lockedItemDao(): LockedItemDao
    abstract fun favoriteListDao(): FavoriteListDao
    abstract fun historyDao(): HistoryDao
    abstract fun epgDao(): EpgDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun vodFavoriteDao(): VodFavoriteDao
    abstract fun traktDao(): TraktDao
    abstract fun recordingDao(): RecordingDao
    abstract fun downloadDao(): DownloadDao
}
