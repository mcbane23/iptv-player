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

/**
 * Liste čiji se sadržaj prikazuje zajedno sa listom `:playlistId`: ona sama, a ako je označena kao
 * spojena (`merged`), i sve ostale spojene liste. Za bilo koju listu iz grupe daje isti skup.
 */
private const val SCOPE_IDS =
    "(SELECT sp.id FROM playlists sp WHERE sp.id = :playlistId OR (sp.merged = 1 " +
        "AND EXISTS(SELECT 1 FROM playlists sm WHERE sm.id = :playlistId AND sm.merged = 1)))"

/** Kanal pripada listi `:playlistId` ili listi spojenoj sa njom. */
private const val IN_SCOPE = "ch.playlistId IN $SCOPE_IDS"

/**
 * Kanal je u spisku ispred kanala sa mestom `:sortOrder` u listi `:ownPlaylistId`. Spojene liste se
 * ređaju jedna za drugom, pa se prvo poredi lista, a tek onda mesto u njoj.
 */
private const val BEFORE_CHANNEL =
    "(ch.playlistId < :ownPlaylistId OR (ch.playlistId = :ownPlaylistId AND ch.sortOrder < :sortOrder))"

private const val AFTER_CHANNEL =
    "(ch.playlistId > :ownPlaylistId OR (ch.playlistId = :ownPlaylistId AND ch.sortOrder > :sortOrder))"

/** Kanali koji su u favoritima. Isti strim u više kategorija se računa jednom. */
private const val FAVORITE_CHANNELS =
    "FROM channels ch JOIN favorites fav ON fav.playlistId = ch.playlistId AND fav.channelKey = ch.stableKey " +
        "WHERE $IN_SCOPE GROUP BY ch.playlistId, ch.stableKey ORDER BY fav.addedAt, ch.playlistId, ch.sortOrder"

/** Kanali jedne imenovane liste omiljenih (`:listId`), redom kojim su dodati. */
private const val LISTED_CHANNELS =
    "FROM channels ch JOIN favorite_list_items li ON li.playlistId = ch.playlistId AND li.channelKey = ch.stableKey " +
        "WHERE li.listId = :listId AND $IN_SCOPE GROUP BY ch.playlistId, ch.stableKey " +
        "ORDER BY li.addedAt, ch.playlistId, ch.sortOrder"

/** Nedavno gledani kanali, najskoriji prvi. */
private const val RECENT_CHANNELS =
    "FROM channels ch JOIN history h ON h.playlistId = ch.playlistId AND h.channelKey = ch.stableKey " +
        "WHERE $IN_SCOPE GROUP BY ch.playlistId, ch.stableKey ORDER BY h.watchedAt DESC, ch.playlistId, ch.sortOrder"

/** Redosled kategorija: prvo ručno raspoređene, pa ostale po redosledu iz liste. */
private const val CATEGORY_ORDER = "ORDER BY cat.userOrder IS NULL, cat.userOrder, cat.playlistId, cat.sortOrder"

/**
 * Isti kanal postoji i u nekoj spojenoj listi koja je u spisku ispred ove: kanal sa potpuno istim
 * nazivom, u kategoriji koja nije sakrivena. U zajedničkim spiskovima se takav kanal prikazuje jednom
 * (iz prve liste), a ostali su mu rezervni izvori, vidi [ChannelDao.sources]. Poređenje je tačno po
 * nazivu, da može da koristi indeks.
 */
private const val DUPLICATE =
    "EXISTS(SELECT 1 FROM channels d WHERE d.playlistId IN $SCOPE_IDS AND d.playlistId < ch.playlistId " +
        "AND d.name = ch.name AND NOT EXISTS(SELECT 1 FROM categories dc WHERE dc.id = d.categoryId AND dc.hidden = 1))"

/**
 * Uslov koji iz spiska „svi kanali” (`:categoryId` je null) izbacuje kanale sakrivenih kategorija.
 * Kad je kategorija zadata, ne menja ništa.
 */
private const val VISIBLE_IN_ALL =
    "AND (:categoryId IS NOT NULL OR (NOT EXISTS(SELECT 1 FROM categories hc " +
        "WHERE hc.id = ch.categoryId AND hc.hidden = 1) AND NOT $DUPLICATE))"

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

    /** Označava da li se kanali liste prikazuju zajedno sa ostalim spojenim listama. */
    @Query("UPDATE playlists SET merged = :merged WHERE id = :id")
    suspend fun setMerged(id: Long, merged: Boolean)

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
            "WHERE cat.playlistId IN $SCOPE_IDS AND cat.type = 'LIVE' AND cat.hidden = 0 $CATEGORY_ORDER",
    )
    fun observeLiveWithCount(playlistId: Long): Flow<List<CategoryWithCount>>

    /** Sve kategorije, i sakrivene (za ekran uređivanja). */
    @Query(
        "SELECT cat.*, $CATEGORY_COUNT FROM categories cat " +
            "WHERE cat.playlistId IN $SCOPE_IDS AND cat.type = 'LIVE' $CATEGORY_ORDER",
    )
    fun observeAllLiveWithCount(playlistId: Long): Flow<List<CategoryWithCount>>

    @Query("SELECT cat.* FROM categories cat WHERE cat.playlistId IN $SCOPE_IDS AND cat.type = 'LIVE' $CATEGORY_ORDER")
    suspend fun getLive(playlistId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId")
    suspend fun getByPlaylist(playlistId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE categories SET hidden = :hidden WHERE playlistId IN $SCOPE_IDS AND type = 'LIVE'")
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
    @Query("SELECT COUNT(*) FROM channels ch WHERE $IN_SCOPE $NOT_HIDDEN AND NOT $DUPLICATE")
    fun observeVisibleCount(playlistId: Long): Flow<Int>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND stableKey = :stableKey LIMIT 1")
    suspend fun byStableKey(playlistId: Long, stableKey: String): ChannelEntity?

    /** Isti strim može biti u više kategorija; vraća onaj čije je mesto u listi najbliže [sortOrder]. */
    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND stableKey = :stableKey " +
            "ORDER BY ABS(sortOrder - :sortOrder) LIMIT 1",
    )
    suspend fun nearestByStableKey(playlistId: Long, stableKey: String, sortOrder: Int): ChannelEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM channels WHERE id = :id)")
    fun observeExists(id: Long): Flow<Boolean>

    /** Kanali jedne kategorije, ili svi kanali liste kad je [categoryId] null. */
    @Query(
        "SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN FROM channels ch WHERE $IN_SCOPE " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL ORDER BY ch.playlistId, ch.sortOrder",
    )
    fun pagingSource(playlistId: Long, categoryId: Long?, now: Long): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.*, 1 AS favorite, $NOW_COLUMN $FAVORITE_CHANNELS")
    fun favoritesPagingSource(playlistId: Long, now: Long): PagingSource<Int, ChannelWithFavorite>

    /** Kanali imenovane liste omiljenih [listId]. */
    @Query("SELECT ch.*, 1 AS favorite, $NOW_COLUMN $LISTED_CHANNELS")
    fun favoriteListPagingSource(playlistId: Long, listId: Long, now: Long): PagingSource<Int, ChannelWithFavorite>

    @Query("SELECT ch.id $LISTED_CHANNELS")
    suspend fun favoriteListIds(playlistId: Long, listId: Long): List<Long>

    @Query("SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN $RECENT_CHANNELS")
    fun recentPagingSource(playlistId: Long, now: Long): PagingSource<Int, ChannelWithFavorite>

    /** Prvih [limit] omiljenih kanala sa emisijom koja je sada na programu, za „Početnu” na TV-u. */
    @Query("SELECT ch.*, 1 AS favorite, $NOW_COLUMN $FAVORITE_CHANNELS LIMIT :limit")
    suspend fun favoritesTop(playlistId: Long, now: Long, limit: Int): List<ChannelWithFavorite>

    /** Prvih [limit] nedavno gledanih kanala, za „Početnu” kad omiljenih nema. */
    @Query("SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN $RECENT_CHANNELS LIMIT :limit")
    suspend fun recentTop(playlistId: Long, now: Long, limit: Int): List<ChannelWithFavorite>

    /** Pretraga po nazivu kroz sve kategorije. U [pattern] su `%`, `_` i `\` već zaštićeni. */
    @Query(
        "SELECT ch.*, $FAVORITE_COLUMN, $NOW_COLUMN FROM channels ch WHERE $IN_SCOPE " +
            "AND ch.name LIKE :pattern ESCAPE '\\' $NOT_HIDDEN AND NOT $DUPLICATE " +
            "ORDER BY ch.name COLLATE NOCASE, ch.playlistId, ch.sortOrder",
    )
    fun searchPagingSource(playlistId: Long, pattern: String, now: Long): PagingSource<Int, ChannelWithFavorite>

    /** Prvih [limit] kanala čiji naziv odgovara, za zajedničku pretragu. Isti strim se računa jednom. */
    @Query(
        "SELECT ch.id AS id, ch.name AS name, ch.logo AS logo FROM channels ch WHERE $IN_SCOPE " +
            "AND ch.name LIKE :pattern ESCAPE '\\' $NOT_HIDDEN GROUP BY ch.playlistId, ch.stableKey " +
            "ORDER BY ch.name COLLATE NOCASE, ch.playlistId, ch.sortOrder LIMIT :limit",
    )
    suspend fun searchTop(playlistId: Long, pattern: String, limit: Int): List<ChannelHit>

    /** Emisije iz vodiča čiji naslov odgovara, a još nisu završene; najskorije prve. */
    @Query(
        "SELECT ch.id AS channelId, ch.name AS channelName, ch.playlistId AS playlistId, ch.stableKey AS stableKey, " +
            "e.startMs AS startMs, e.stopMs AS stopMs, e.title AS title " +
            "FROM epg_programs e JOIN channels ch ON ch.playlistId = e.playlistId AND ch.epgKey = e.channelKey " +
            "WHERE $IN_SCOPE AND e.stopMs > :now AND e.title LIKE :pattern ESCAPE '\\' $NOT_HIDDEN " +
            "GROUP BY ch.playlistId, ch.stableKey, e.startMs ORDER BY e.startMs, ch.playlistId, ch.sortOrder LIMIT :limit",
    )
    suspend fun searchPrograms(playlistId: Long, pattern: String, now: Long, limit: Int): List<ProgramHit>

    @Query("SELECT ch.id $FAVORITE_CHANNELS")
    suspend fun favoriteIds(playlistId: Long): List<Long>

    @Query("SELECT ch.id $RECENT_CHANNELS")
    suspend fun recentIds(playlistId: Long): List<Long>

    @Query(
        "SELECT COUNT(DISTINCT ch.playlistId || '|' || ch.stableKey) FROM channels ch JOIN favorites fav " +
            "ON fav.playlistId = ch.playlistId AND fav.channelKey = ch.stableKey WHERE $IN_SCOPE",
    )
    fun observeFavoriteCount(playlistId: Long): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT ch.playlistId || '|' || ch.stableKey) FROM channels ch JOIN history h " +
            "ON h.playlistId = ch.playlistId AND h.channelKey = ch.stableKey WHERE $IN_SCOPE",
    )
    fun observeRecentCount(playlistId: Long): Flow<Int>

    /** Redni broj kanala (od nule) unutar kategorije, odnosno cele liste. */
    @Query(
        "SELECT COUNT(*) FROM channels ch WHERE $IN_SCOPE " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL AND $BEFORE_CHANNEL",
    )
    suspend fun positionOf(playlistId: Long, categoryId: Long?, ownPlaylistId: Long, sortOrder: Int): Int

    @Query(
        "SELECT ch.* FROM channels ch WHERE $IN_SCOPE " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL AND $AFTER_CHANNEL " +
            "ORDER BY ch.playlistId, ch.sortOrder LIMIT 1",
    )
    suspend fun next(playlistId: Long, categoryId: Long?, ownPlaylistId: Long, sortOrder: Int): ChannelEntity?

    @Query(
        "SELECT ch.* FROM channels ch WHERE $IN_SCOPE " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL AND $BEFORE_CHANNEL " +
            "ORDER BY ch.playlistId DESC, ch.sortOrder DESC LIMIT 1",
    )
    suspend fun previous(playlistId: Long, categoryId: Long?, ownPlaylistId: Long, sortOrder: Int): ChannelEntity?

    @Query(
        "SELECT ch.* FROM channels ch WHERE $IN_SCOPE " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL ORDER BY ch.playlistId, ch.sortOrder LIMIT 1",
    )
    suspend fun first(playlistId: Long, categoryId: Long?): ChannelEntity?

    @Query(
        "SELECT ch.* FROM channels ch WHERE $IN_SCOPE " +
            "AND (:categoryId IS NULL OR ch.categoryId = :categoryId) $VISIBLE_IN_ALL " +
            "ORDER BY ch.playlistId DESC, ch.sortOrder DESC LIMIT 1",
    )
    suspend fun last(playlistId: Long, categoryId: Long?): ChannelEntity?

    @Query("SELECT ch.* FROM channels ch WHERE $IN_SCOPE AND ch.number = :number ORDER BY ch.playlistId, ch.sortOrder LIMIT 1")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    @Query("SELECT id, name, tvgId, catchupType, stableKey FROM channels WHERE playlistId = :playlistId")
    suspend fun epgRows(playlistId: Long): List<ChannelEpgRow>

    @Query("UPDATE channels SET epgKey = :epgKey WHERE id IN (:ids)")
    suspend fun setEpgKey(ids: List<Long>, epgKey: String)

    /**
     * Isti kanal (potpuno isti naziv) u listi `:playlistId` i u listama spojenim sa njom, po jedan iz
     * svake liste: prvo iz same liste, pa iz ostalih. Kad liste nisu spojene, vraća samo jedan red.
     */
    @Query(
        "SELECT ch.id AS id, ch.playlistId AS playlistId, p.name AS playlistName, MIN(ch.sortOrder) AS firstOrder " +
            "FROM channels ch JOIN playlists p ON p.id = ch.playlistId WHERE $IN_SCOPE AND ch.name = :name " +
            "GROUP BY ch.playlistId ORDER BY ch.playlistId != :playlistId, ch.playlistId",
    )
    suspend fun sources(playlistId: Long, name: String): List<ChannelSourceRow>

    @Query("UPDATE channels SET epgKey = :epgKey WHERE id = :id")
    suspend fun setEpgKeyOf(id: Long, epgKey: String?)

    /** Posle osvežavanja liste vraća kanalima ručno izabran kanal iz vodiča (vidi [EpgLinkEntity]). */
    @Query(
        "UPDATE channels SET epgKey = (SELECT l.epgKey FROM epg_links l WHERE l.playlistId = channels.playlistId " +
            "AND l.stableKey = channels.stableKey) WHERE playlistId = :playlistId AND EXISTS (" +
            "SELECT 1 FROM epg_links l WHERE l.playlistId = channels.playlistId AND l.stableKey = channels.stableKey)",
    )
    suspend fun applyEpgLinks(playlistId: Long)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Query("DELETE FROM epg_channels WHERE playlistId = :playlistId")
    suspend fun deleteChannels(playlistId: Long)

    @Query("SELECT * FROM epg_channels WHERE playlistId = :playlistId")
    suspend fun guideChannels(playlistId: Long): List<EpgChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(link: EpgLinkEntity)

    @Query("DELETE FROM epg_links WHERE playlistId = :playlistId AND stableKey = :stableKey")
    suspend fun deleteLink(playlistId: Long, stableKey: String)

    @Query("SELECT * FROM epg_links WHERE playlistId = :playlistId")
    suspend fun links(playlistId: Long): List<EpgLinkEntity>

    @Query("SELECT epgKey FROM epg_links WHERE playlistId = :playlistId AND stableKey = :stableKey")
    suspend fun linkedKey(playlistId: Long, stableKey: String): String?

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

/** Liste omiljenih kanala kojima je korisnik dao ime. */
@Dao
interface FavoriteListDao {
    @Insert
    suspend fun insert(list: FavoriteListEntity): Long

    @Query("UPDATE favorite_lists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM favorite_lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM favorite_lists")
    suspend fun nextPosition(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_lists WHERE name = :name COLLATE NOCASE AND id != :exceptId)")
    suspend fun nameTaken(name: String, exceptId: Long): Boolean

    @Query("SELECT COUNT(*) FROM favorite_lists")
    suspend fun count(): Int

    @Query("SELECT * FROM favorite_lists ORDER BY position, id")
    suspend fun all(): List<FavoriteListEntity>

    /** Sve liste, sa brojem kanala koji se iz svake vide u listi kanala [playlistId] i spojenima sa njom. */
    @Query(
        "SELECT fl.id, fl.name, (SELECT COUNT(DISTINCT ch.playlistId || '|' || ch.stableKey) FROM channels ch " +
            "JOIN favorite_list_items li ON li.playlistId = ch.playlistId AND li.channelKey = ch.stableKey " +
            "WHERE li.listId = fl.id AND $IN_SCOPE) AS channelCount FROM favorite_lists fl ORDER BY fl.position, fl.id",
    )
    fun observe(playlistId: Long): Flow<List<FavoriteListRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: FavoriteListItemEntity)

    @Query("DELETE FROM favorite_list_items WHERE listId = :listId AND playlistId = :playlistId AND channelKey = :channelKey")
    suspend fun deleteItem(listId: Long, playlistId: Long, channelKey: String)

    /** Uklanja kanal iz svih imenovanih lista (kad prestane da bude omiljen). */
    @Query("DELETE FROM favorite_list_items WHERE playlistId = :playlistId AND channelKey = :channelKey")
    suspend fun deleteItems(playlistId: Long, channelKey: String)

    /** Liste u kojima je kanal. */
    @Query("SELECT listId FROM favorite_list_items WHERE playlistId = :playlistId AND channelKey = :channelKey")
    suspend fun listsOf(playlistId: Long, channelKey: String): List<Long>

    /** Ključevi kanala liste [playlistId] u imenovanoj listi [listId], redom kojim su dodati (za prenos na TV). */
    @Query("SELECT channelKey FROM favorite_list_items WHERE listId = :listId AND playlistId = :playlistId ORDER BY addedAt")
    suspend fun keys(listId: Long, playlistId: Long): List<String>
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
