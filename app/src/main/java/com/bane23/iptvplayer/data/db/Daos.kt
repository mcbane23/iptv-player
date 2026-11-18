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

/** Emisija koja je u trenutku `:now` na programu kanala (uslov za podupit). */
private const val NOW_PROGRAM =
    "FROM epg_programs e WHERE e.playlistId = ch.playlistId AND e.channelKey = ch.epgKey " +
        "AND e.startMs <= :now AND e.stopMs > :now ORDER BY e.startMs DESC LIMIT 1"

/** Naslov, početak i kraj emisije koja je u trenutku `:now` na programu kanala. */
private const val NOW_COLUMN =
    "(SELECT e.title $NOW_PROGRAM) AS nowTitle, " +
        "(SELECT e.startMs $NOW_PROGRAM) AS nowStartMs, " +
        "(SELECT e.stopMs $NOW_PROGRAM) AS nowStopMs"

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

/**
 * Uslov koji iz spiska „svi kanali” (`:categoryId` je null) izbacuje kanale sakrivenih kategorija.
 * Kad je kategorija zadata, ne menja ništa.
 */
private const val VISIBLE_IN_ALL =
    "AND (:categoryId IS NOT NULL OR NOT EXISTS(SELECT 1 FROM categories hc " +
        "WHERE hc.id = ch.categoryId AND hc.hidden = 1))"

/** Kanal nije u sakrivenoj kategoriji. */
private const val NOT_HIDDEN =
    "AND NOT EXISTS(SELECT 1 FROM categories hc WHERE hc.id = ch.categoryId AND hc.hidden = 1)"

private const val CATEGORY_COUNT =
    "(SELECT COUNT(*) FROM channels ch WHERE ch.playlistId = cat.playlistId AND ch.categoryId = cat.id) AS channelCount"

/** Kanal u rezultatima zajedničke pretrage. */
data class ChannelHit(val id: Long, val name: String, val logo: String?)

/** Emisija u rezultatima zajedničke pretrage, sa kanalom na kom je. */
data class ProgramHit(
    val channelId: Long,
    val channelName: String,
    val playlistId: Long,
    val stableKey: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
) {
    /** Isti oblik kao ključ emisije u vodiču i ključ podsetnika. */
    val key: String get() = "$playlistId|$stableKey|$startMs"
}

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

    @Query("SELECT * FROM playlists")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("UPDATE playlists SET epgRefreshedAt = :time WHERE id = :id")
    suspend fun markEpgRefreshed(id: Long, time: Long)

    @Query("UPDATE playlists SET accountExpiresAt = :expiresAt, maxConnections = :maxConnections WHERE id = :id")
    suspend fun setAccountInfo(id: Long, expiresAt: Long?, maxConnections: Int?)

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

    @Query("UPDATE categories SET hidden = :hidden WHERE playlistId = :playlistId AND type = 'LIVE'")
    suspend fun setAllLiveHidden(playlistId: Long, hidden: Boolean)

    @Query("UPDATE categories SET userOrder = :order WHERE id = :id")
    suspend fun setUserOrder(id: Long, order: Int)

    /** Vraća sve kategorije liste na vidljive i u redosled provajdera. */
    @Query("UPDATE categories SET hidden = 0, userOrder = NULL WHERE playlistId = :playlistId")
    suspend fun resetUserChanges(playlistId: Long)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface ChannelDao {
    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    /** Broj kanala u spisku „svi kanali”: bez kanala sakrivenih kategorija. */
    @Query("SELECT COUNT(*) FROM channels ch WHERE ch.playlistId = :playlistId $NOT_HIDDEN")
    fun observeVisibleCount(playlistId: Long): Flow<Int>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND stableKey = :stableKey LIMIT 1")
    suspend fun byStableKey(playlistId: Long, stableKey: String): ChannelEntity?

    /** Kanali jedne kategorije, ili svi kanali liste kad je [categoryId] null. */
    @Query(
        "SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL ORDER BY ch.sortOrder",
    )
    fun pagingSource(playlistId: Long, categoryId: Long?, now: Long): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.*, 1 AS favorite, $NOW_COLUMN $FAVORITE_CHANNELS")
    fun favoritesPagingSource(playlistId: Long, now: Long): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN $RECENT_CHANNELS")
    fun recentPagingSource(playlistId: Long, now: Long): PagingSource<Int, ChannelWithFavorite>

    /** Pretraga po nazivu kroz sve kategorije. U [pattern] su `%`, `_` i `\` već zaštićeni. */
    @Query(
        "SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND ch.name LIKE :pattern ESCAPE '\\' $NOT_HIDDEN ORDER BY ch.name COLLATE NOCASE, ch.sortOrder",
    )
    fun searchPagingSource(playlistId: Long, pattern: String, now: Long): PagingSource<Int, ChannelWithFavorite>

    /** Prvih [limit] kanala čiji naziv odgovara, za zajedničku pretragu. Isti strim se računa jednom. */
    @Query(
        "SELECT ch.id AS id, ch.name AS name, ch.logo AS logo FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND ch.name LIKE :pattern ESCAPE '\\' $NOT_HIDDEN GROUP BY ch.stableKey " +
            "ORDER BY ch.name COLLATE NOCASE, ch.sortOrder LIMIT :limit",
    )
    suspend fun searchTop(playlistId: Long, pattern: String, limit: Int): List<ChannelHit>

    /** Emisije iz vodiča čiji naslov odgovara, a još nisu završene; najskorije prve. */
    @Query(
        "SELECT ch.id AS channelId, ch.name AS channelName, ch.playlistId AS playlistId, ch.stableKey AS stableKey, " +
            "e.startMs AS startMs, e.stopMs AS stopMs, e.title AS title " +
            "FROM epg_programs e JOIN channels ch ON ch.playlistId = e.playlistId AND ch.epgKey = e.channelKey " +
            "WHERE e.playlistId = :playlistId AND e.stopMs > :now AND e.title LIKE :pattern ESCAPE '\\' $NOT_HIDDEN " +
            "GROUP BY ch.stableKey, e.startMs ORDER BY e.startMs, ch.sortOrder LIMIT :limit",
    )
    suspend fun searchPrograms(playlistId: Long, pattern: String, now: Long, limit: Int): List<ProgramHit>

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
        "SELECT COUNT(*) FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL AND ch.sortOrder < :sortOrder",
    )
    suspend fun positionOf(playlistId: Long, categoryId: Long?, sortOrder: Int): Int

    @Query(
        "SELECT ch.* FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL AND ch.sortOrder > :sortOrder " +
            "ORDER BY ch.sortOrder LIMIT 1",
    )
    suspend fun next(playlistId: Long, categoryId: Long?, sortOrder: Int): ChannelEntity?

    @Query(
        "SELECT ch.* FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL AND ch.sortOrder < :sortOrder " +
            "ORDER BY ch.sortOrder DESC LIMIT 1",
    )
    suspend fun previous(playlistId: Long, categoryId: Long?, sortOrder: Int): ChannelEntity?

    @Query(
        "SELECT ch.* FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL ORDER BY ch.sortOrder LIMIT 1",
    )
    suspend fun first(playlistId: Long, categoryId: Long?): ChannelEntity?

    @Query(
        "SELECT ch.* FROM channels ch WHERE ch.playlistId = :playlistId " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL ORDER BY ch.sortOrder DESC LIMIT 1",
    )
    suspend fun last(playlistId: Long, categoryId: Long?): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number ORDER BY sortOrder LIMIT 1")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("SELECT id, name, tvgId, catchupType FROM channels WHERE playlistId = :playlistId")
    suspend fun epgRows(playlistId: Long): List<ChannelEpgRow>

    @Query("UPDATE channels SET epgKey = :epgKey WHERE id IN (:ids)")
    suspend fun setEpgKey(ids: List<Long>, epgKey: String)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface EpgDao {
    @Insert
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    /** Emisija koja je u toku i one posle nje. */
    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelKey = :channelKey " +
            "AND stopMs > :now GROUP BY startMs ORDER BY startMs LIMIT :limit",
    )
    suspend fun upcoming(playlistId: Long, channelKey: String, now: Long, limit: Int): List<EpgProgramEntity>

    /** Emisije kanala koje se bar delom prikazuju u periodu od [from] do [to]. */
    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelKey = :channelKey " +
            "AND stopMs > :from AND startMs < :to GROUP BY startMs ORDER BY startMs",
    )
    suspend fun range(playlistId: Long, channelKey: String, from: Long, to: Long): List<EpgProgramEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM epg_programs WHERE playlistId = :playlistId)")
    fun observeHasPrograms(playlistId: Long): Flow<Boolean>

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId AND generation != :generation")
    suspend fun deleteOtherGenerations(playlistId: Long, generation: Long)

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId AND generation = :generation")
    suspend fun deleteGeneration(playlistId: Long, generation: Long)

    /** Početak najranije emisije kanala u datom učitavanju, ili null ako ih nema. */
    @Query(
        "SELECT MIN(startMs) FROM epg_programs WHERE playlistId = :playlistId AND channelKey = :channelKey " +
            "AND generation = :generation",
    )
    suspend fun firstStart(playlistId: Long, channelKey: String, generation: Long): Long?

    /**
     * Stare emisije kanala koje su počele pre [before] prelaze u novo učitavanje, da ih ono ne
     * obriše. Tako se za kanale sa arhivom čuva i prošlost koje u novom vodiču više nema.
     */
    @Query(
        "UPDATE epg_programs SET generation = :generation WHERE playlistId = :playlistId " +
            "AND channelKey = :channelKey AND generation != :generation AND startMs < :before",
    )
    suspend fun adoptEarlier(playlistId: Long, channelKey: String, generation: Long, before: Long)

    /**
     * Briše emisije završene pre [cutoff]. Za kanale sa arhivom iz M3U liste emisije se čuvaju
     * duže, do [archiveCutoff], jer se spisak arhive pravi iz sačuvanog vodiča.
     */
    @Query(
        "DELETE FROM epg_programs WHERE stopMs < :cutoff AND (stopMs < :archiveCutoff OR NOT EXISTS (" +
            "SELECT 1 FROM channels c WHERE c.playlistId = epg_programs.playlistId " +
            "AND c.epgKey = epg_programs.channelKey AND c.catchupType IS NOT NULL AND c.catchupType != 'xtream'))",
    )
    suspend fun deleteEndedBefore(cutoff: Long, archiveCutoff: Long)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    /** Ključevi omiljenih kanala liste, redom kojim su dodati. */
    @Query("SELECT channelKey FROM favorites WHERE playlistId = :playlistId ORDER BY addedAt")
    suspend fun keysByPlaylist(playlistId: Long): List<String>

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

@Dao
interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemId = :itemId")
    suspend fun get(playlistId: Long, kind: String, itemId: Long): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemId = :itemId")
    fun observe(playlistId: Long, kind: String, itemId: Long): Flow<WatchProgressEntity?>

    /** Napredak svih epizoda jedne serije, poslednje gledana prva. */
    @Query(
        "SELECT * FROM watch_progress WHERE playlistId = :playlistId AND seriesId = :seriesId " +
            "AND kind = 'episode' ORDER BY updatedAt DESC",
    )
    fun observeSeries(playlistId: Long, seriesId: Long): Flow<List<WatchProgressEntity>>

    /** Filmovi koji su započeti, a nisu odgledani do kraja ni sklonjeni iz reda; poslednje gledan prvi. */
    @Query(
        "SELECT * FROM watch_progress WHERE playlistId = :playlistId AND kind = 'movie' AND title IS NOT NULL " +
            "AND dismissedAt IS NULL AND positionMs >= :minPositionMs AND positionMs * 100 < durationMs * :finishedPercent " +
            "ORDER BY updatedAt DESC LIMIT :limit",
    )
    fun observeContinueMovies(
        playlistId: Long,
        minPositionMs: Long,
        finishedPercent: Int,
        limit: Int,
    ): Flow<List<WatchProgressEntity>>

    /**
     * Poslednje gledana epizoda svake serije; poslednje gledana serija prva. Serija sklonjena iz reda
     * se vraća tek kad se neka njena epizoda gleda posle sklanjanja.
     */
    @Query(
        "SELECT * FROM watch_progress w WHERE w.playlistId = :playlistId AND w.kind = 'episode' " +
            "AND w.title IS NOT NULL AND w.dismissedAt IS NULL AND w.updatedAt = (SELECT MAX(w2.updatedAt) FROM watch_progress w2 " +
            "WHERE w2.playlistId = w.playlistId AND w2.kind = 'episode' AND w2.seriesId = w.seriesId) " +
            "ORDER BY w.updatedAt DESC LIMIT :limit",
    )
    fun observeContinueSeries(playlistId: Long, limit: Int): Flow<List<WatchProgressEntity>>

    /** Serije liste čiju je bar jednu epizodu korisnik gledao ili označio u aplikaciji. */
    @Query("SELECT DISTINCT seriesId FROM watch_progress WHERE playlistId = :playlistId AND kind = 'episode'")
    suspend fun seriesWithProgress(playlistId: Long): List<Long>

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemId IN (:itemIds)")
    suspend fun getMany(playlistId: Long, kind: String, itemIds: List<Long>): List<WatchProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WatchProgressEntity>)

    @Query("DELETE FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemId IN (:itemIds)")
    suspend fun deleteMany(playlistId: Long, kind: String, itemIds: List<Long>)

    /** Sklanja film iz reda „Nastavi gledanje”; pozicija ostaje. */
    @Query("UPDATE watch_progress SET dismissedAt = :at WHERE playlistId = :playlistId AND kind = 'movie' AND itemId = :movieId")
    suspend fun dismissMovie(playlistId: Long, movieId: Long, at: Long)

    /** Sklanja seriju iz reda „Nastavi gledanje”: oznaku dobijaju sve njene do sada gledane epizode. */
    @Query("UPDATE watch_progress SET dismissedAt = :at WHERE playlistId = :playlistId AND kind = 'episode' AND seriesId = :seriesId")
    suspend fun dismissSeries(playlistId: Long, seriesId: Long, at: Long)

    /** Kad je serija poslednji put sklonjena iz reda „Nastavi gledanje”; null ako nije. */
    @Query(
        "SELECT MAX(dismissedAt) FROM watch_progress WHERE playlistId = :playlistId AND kind = 'episode' " +
            "AND seriesId = :seriesId",
    )
    suspend fun seriesDismissedAt(playlistId: Long, seriesId: Long): Long?

    /** Sve što je označeno kao odgledano, za prvo slanje na Trakt posle prijave. */
    @Query("SELECT * FROM watch_progress WHERE watchedAt IS NOT NULL")
    suspend fun allWatched(): List<WatchProgressEntity>

    /** ID-jevi odgledanih filmova, odnosno epizoda, za kvačicu na posteru. */
    @Query("SELECT itemId FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND watchedAt IS NOT NULL")
    fun observeWatchedIds(playlistId: Long, kind: String): Flow<List<Long>>
}

@Dao
interface VodFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: VodFavoriteEntity)

    /** Dodaje samo ono što još nije u omiljenima; postojeće ostaje kako jeste. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(favorites: List<VodFavoriteEntity>)

    @Query("DELETE FROM vod_favorites WHERE playlistId = :playlistId AND kind = :kind AND itemId = :itemId")
    suspend fun delete(playlistId: Long, kind: String, itemId: Long)

    /** Svi omiljeni filmovi i serije liste, redom kojim su dodati. */
    @Query("SELECT * FROM vod_favorites WHERE playlistId = :playlistId ORDER BY addedAt")
    suspend fun getByPlaylist(playlistId: Long): List<VodFavoriteEntity>

    /** Omiljeni filmovi, odnosno serije, liste; poslednje dodat prvi. */
    @Query("SELECT * FROM vod_favorites WHERE playlistId = :playlistId AND kind = :kind ORDER BY addedAt DESC")
    fun observe(playlistId: Long, kind: String): Flow<List<VodFavoriteEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM vod_favorites WHERE playlistId = :playlistId AND kind = :kind AND itemId = :itemId)",
    )
    fun observeIsFavorite(playlistId: Long, kind: String, itemId: Long): Flow<Boolean>
}

@Dao
interface TraktDao {
    @Insert
    suspend fun addPending(rows: List<TraktPendingEntity>)

    @Query("SELECT * FROM trakt_pending ORDER BY id")
    suspend fun pending(): List<TraktPendingEntity>

    @Query("DELETE FROM trakt_pending WHERE id IN (:ids)")
    suspend fun deletePending(ids: List<Long>)

    @Query("DELETE FROM trakt_pending")
    suspend fun clearPending()

    @Query("SELECT tmdbId FROM trakt_watched WHERE kind = 'movie'")
    fun observeWatchedMovies(): Flow<List<Long>>

    @Query("SELECT * FROM trakt_watched WHERE kind = 'episode' AND tmdbId = :showTmdbId")
    fun observeWatchedEpisodes(showTmdbId: Long): Flow<List<TraktWatchedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatched(rows: List<TraktWatchedEntity>)

    @Query("DELETE FROM trakt_watched WHERE kind = :kind AND tmdbId = :tmdbId AND season = :season AND episode = :episode")
    suspend fun deleteWatched(kind: String, tmdbId: Long, season: Int, episode: Int)

    @Query("DELETE FROM trakt_watched")
    suspend fun clearWatched()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPlayback(row: TraktPlaybackPendingEntity)

    @Query("SELECT * FROM trakt_playback_pending ORDER BY createdAt")
    suspend fun pendingPlayback(): List<TraktPlaybackPendingEntity>

    @Query("DELETE FROM trakt_playback_pending WHERE playlistId = :playlistId AND kind = :kind AND itemId = :itemId")
    suspend fun deletePlayback(playlistId: Long, kind: String, itemId: Long)

    @Query("DELETE FROM trakt_playback_pending WHERE playlistId = :playlistId AND kind = 'episode' AND seriesId = :seriesId")
    suspend fun deleteSeriesPlayback(playlistId: Long, seriesId: Long)

    @Query("DELETE FROM trakt_playback_pending")
    suspend fun clearPlayback()
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status = :status")
    suspend fun byStatus(status: String): List<RecordingEntity>

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("UPDATE recordings SET status = 'recording', path = :path, startedAt = :startedAt WHERE id = :id")
    suspend fun markRecording(id: Long, path: String, startedAt: Long)

    @Query("UPDATE recordings SET sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun setSize(id: Long, sizeBytes: Long)

    @Query("UPDATE recordings SET status = :status, error = :error, endedAt = :endedAt, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun finish(id: Long, status: String, error: String?, endedAt: Long, sizeBytes: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}
