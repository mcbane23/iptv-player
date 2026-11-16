package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.VodFavoriteEntity
import com.bane23.iptvplayer.data.db.WatchProgressEntity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.data.xtream.XtreamApi
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.domain.StreamSource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

enum class VodKind { MOVIES, SERIES }

data class VodCategory(val id: String, val name: String)

/** Film ili serija u spisku. */
data class VodItem(
    val id: Long,
    val name: String,
    val poster: String?,
    val rating: String?,
    /** Nastavak fajla filma; za serije null. */
    val extension: String?,
)

/** Zapamćena pozicija gledanja filma ili epizode. */
data class WatchProgress(val itemId: Long, val positionMs: Long, val durationMs: Long) {
    /** Odgledano skoro do kraja: nema smisla nuditi nastavak. */
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * FINISHED_PERCENT / 100

    /** Vredi ponuditi nastavak (nije na samom početku ni na kraju). */
    val resumable: Boolean get() = positionMs >= MIN_RESUME_MS && !finished

    companion object {
        const val FINISHED_PERCENT = 95
        const val MIN_RESUME_MS = 30_000L
    }
}

/**
 * Stavka reda „Nastavi gledanje”: film ([seriesId] null) ili serija sa poslednje gledanom epizodom.
 * [id] je ID filma, odnosno serije; [progress] je deo koji je odgledan (0 do 1).
 */
data class ContinueItem(
    val id: Long,
    val name: String,
    val poster: String?,
    val extension: String?,
    val progress: Float,
    val isSeries: Boolean,
)

/** Lista nije Xtream nalog, pa nema filmove i serije. */
class VodUnavailableException : IOException("Playlist has no movies or series")

/**
 * Filmovi i serije Xtream naloga. Spiskovi se ne čuvaju u bazi nego se preuzimaju po kategoriji
 * kad zatrebaju i pamte dok aplikacija radi; u bazi je samo dokle je korisnik stigao sa gledanjem.
 */
@Singleton
class VodRepository @Inject constructor(
    db: AppDatabase,
    private val settings: SettingsStore,
    private val api: XtreamApi,
    private val cipher: PasswordCipher,
    private val locks: LockRepository,
) {
    private val playlistDao = db.playlistDao()
    private val progressDao = db.watchProgressDao()
    private val favoriteDao = db.vodFavoriteDao()

    private class Session(
        val playlistId: Long,
        val server: HttpUrl,
        val username: String,
        val password: String,
        val userAgent: String?,
    )

    private val categoryCache = ConcurrentHashMap<String, List<VodCategory>>()
    private val itemCache = ConcurrentHashMap<String, List<VodItem>>()
    private val seriesCache = ConcurrentHashMap<String, SeriesDetails>()

    /** Upis pozicije mora da se završi i kad se ekran plejera zatvori. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Kategorije za prikaz. Kategorija koja je sada zaključana ima katanac ispred naziva. */
    suspend fun categories(kind: VodKind): List<VodCategory> {
        val all = allCategories(kind)
        val keys = locks.activeKeysNow()
        if (keys.isEmpty()) return all
        val playlistId = settings.activePlaylistId.first() ?: return all
        return all.map { category ->
            val locked = LockRepository.vodKey(playlistId, kind == VodKind.SERIES, category.id) in keys
            if (locked) category.copy(name = LockRepository.LOCK_MARK + category.name) else category
        }
    }

    /** Kategorije onako kako ih šalje server, za ekran roditeljske zaštite. */
    suspend fun allCategories(kind: VodKind): List<VodCategory> = withContext(Dispatchers.IO) {
        val session = session()
        categoryCache.getOrPutSuspending("${session.playlistId}/$kind") {
            val action = if (kind == VodKind.MOVIES) XtreamUrls.ACTION_VOD_CATEGORIES else XtreamUrls.ACTION_SERIES_CATEGORIES
            api.categories(session.api(action), session.userAgent).mapNotNull { category ->
                val id = category.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VodCategory(id, category.name?.takeIf { it.isNotBlank() } ?: id)
            }
        }
    }

    /**
     * Spisak kategorije bez sadržaja zaključanih kategorija (roditeljska zaštita). Zaključana
     * kategorija je prazna, a iz spiska „sve” nestaju filmovi i serije koji joj pripadaju.
     */
    suspend fun items(kind: VodKind, categoryId: String): List<VodItem> {
        val hidden = lockedIds(kind)
        val all = loadItems(kind, categoryId)
        return if (hidden.isEmpty()) all else all.filter { it.id !in hidden }
    }

    /** Da li film, odnosno serija, pripada kategoriji koja je sada zaključana. */
    suspend fun isLocked(kind: VodKind, itemId: Long): Boolean = itemId in lockedIds(kind)

    /** ID-jevi filmova, odnosno serija, iz kategorija koje su sada zaključane. */
    private suspend fun lockedIds(kind: VodKind): Set<Long> {
        val keys = locks.activeKeysNow()
        if (keys.isEmpty()) return emptySet()
        val playlistId = settings.activePlaylistId.first() ?: return emptySet()
        val series = kind == VodKind.SERIES
        val prefix = LockRepository.vodKey(playlistId, series, "")
        val categoryIds = keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        val ids = HashSet<Long>()
        for (categoryId in categoryIds) loadItems(kind, categoryId).mapTo(ids) { it.id }
        return ids
    }

    /** Isti spisak, ali bez stavki iz kategorija koje su sada zaključane. */
    private fun <T> Flow<List<T>>.withoutLocked(kind: VodKind, id: (T) -> Long): Flow<List<T>> =
        combine(this, locks.activeKeys) { list, keys -> list to keys }.map { (list, keys) ->
            if (keys.isEmpty()) return@map list
            val hidden = try {
                lockedIds(kind)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bez spiska sa servera ne zna se šta je zaključano, pa se radije ne prikazuje ništa.
                return@map emptyList()
            }
            list.filter { id(it) !in hidden }
        }

    private suspend fun loadItems(kind: VodKind, categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val session = session()
        itemCache.getOrPutSuspending("${session.playlistId}/$kind/$categoryId") {
            if (kind == VodKind.MOVIES) {
                api.vodStreams(session.listUrl(XtreamUrls.ACTION_VOD_STREAMS, categoryId), session.userAgent)
                    .mapNotNull { stream ->
                        val id = stream.streamId?.toLongOrNull() ?: return@mapNotNull null
                        VodItem(
                            id = id,
                            name = stream.name?.takeIf { it.isNotBlank() } ?: id.toString(),
                            poster = stream.icon?.takeIf { it.isNotBlank() },
                            rating = stream.rating.cleanRating(),
                            extension = stream.extension?.takeIf { it.isNotBlank() },
                        )
                    }
            } else {
                api.series(session.listUrl(XtreamUrls.ACTION_SERIES, categoryId), session.userAgent)
                    .mapNotNull { series ->
                        val id = series.seriesId?.toLongOrNull() ?: return@mapNotNull null
                        VodItem(
                            id = id,
                            name = series.name?.takeIf { it.isNotBlank() } ?: id.toString(),
                            poster = series.cover?.takeIf { it.isNotBlank() },
                            rating = series.rating.cleanRating(),
                            extension = null,
                        )
                    }
            }
        }
    }

    suspend fun movie(movieId: Long): MovieDetails = withContext(Dispatchers.IO) {
        val session = session()
        val json = api.details(session.api(XtreamUrls.ACTION_VOD_INFO, "vod_id", movieId.toString()), session.userAgent)
        XtreamVodParser.movie(json)
    }

    suspend fun series(seriesId: Long): SeriesDetails = withContext(Dispatchers.IO) {
        val session = session()
        seriesCache.getOrPutSuspending("${session.playlistId}/$seriesId") {
            val url = session.api(XtreamUrls.ACTION_SERIES_INFO, "series_id", seriesId.toString())
            XtreamVodParser.series(api.details(url, session.userAgent))
        }
    }

    suspend fun movieSource(movieId: Long, extension: String): StreamSource = withContext(Dispatchers.IO) {
        val session = session()
        val url = XtreamUrls.movieStream(session.server, session.username, session.password, movieId, extension)
        StreamSource(listOf(url), session.userAgent, referrer = null)
    }

    suspend fun episodeSource(episodeId: Long, extension: String): StreamSource = withContext(Dispatchers.IO) {
        val session = session()
        val url = XtreamUrls.episodeStream(session.server, session.username, session.password, episodeId, extension)
        StreamSource(listOf(url), session.userAgent, referrer = null)
    }

    suspend fun movieProgress(movieId: Long): WatchProgress? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return progressDao.get(playlistId, KIND_MOVIE, movieId)?.toDomain()
    }

    fun observeMovieProgress(playlistId: Long, movieId: Long): Flow<WatchProgress?> =
        progressDao.observe(playlistId, KIND_MOVIE, movieId).map { it?.toDomain() }

    suspend fun episodeProgress(episodeId: Long): WatchProgress? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return progressDao.get(playlistId, KIND_EPISODE, episodeId)?.toDomain()
    }

    /** Napredak epizoda serije, poslednje gledana prva. */
    fun observeSeriesProgress(playlistId: Long, seriesId: Long): Flow<List<WatchProgress>> =
        progressDao.observeSeries(playlistId, seriesId).map { rows -> rows.map { it.toDomain() } }

    val activePlaylistId: Flow<Long?> = settings.activePlaylistId

    /** Filmovi, odnosno serije, koje je korisnik počeo da gleda; poslednje gledano prvo. */
    fun observeContinue(playlistId: Long, kind: VodKind): Flow<List<ContinueItem>> {
        val rows = if (kind == VodKind.MOVIES) {
            progressDao.observeContinueMovies(
                playlistId,
                WatchProgress.MIN_RESUME_MS,
                WatchProgress.FINISHED_PERCENT,
                CONTINUE_LIMIT,
            )
        } else {
            progressDao.observeContinueSeries(playlistId, CONTINUE_LIMIT)
        }
        return rows.withoutLocked(kind) { if (kind == VodKind.SERIES) it.seriesId else it.itemId }.map { list ->
            list.map { row ->
                val isSeries = kind == VodKind.SERIES
                ContinueItem(
                    id = if (isSeries) row.seriesId else row.itemId,
                    name = row.title.orEmpty(),
                    poster = row.poster,
                    extension = row.extension,
                    progress = if (row.durationMs > 0) (row.positionMs.toFloat() / row.durationMs).coerceIn(0f, 1f) else 0f,
                    isSeries = isSeries,
                )
            }
        }
    }

    /**
     * Svi filmovi, odnosno sve serije liste, za pretragu kroz sve kategorije.
     * Učitava se jednim zahtevom (bez kategorije) i čuva u memoriji kao i spiskovi kategorija.
     */
    suspend fun allItems(kind: VodKind): List<VodItem> = items(kind, ALL_CATEGORIES)

    /**
     * Pamti poziciju gledanja. [seriesId] je null za film. [title] i [poster] su naziv i poster filma,
     * odnosno serije, za red „Nastavi gledanje”. Ne čeka da se upis završi.
     */
    fun saveProgress(
        itemId: Long,
        seriesId: Long?,
        positionMs: Long,
        durationMs: Long,
        title: String? = null,
        poster: String? = null,
        extension: String? = null,
    ) {
        if (durationMs <= 0) return
        writeScope.launch {
            val playlistId = settings.activePlaylistId.first() ?: return@launch
            progressDao.upsert(
                WatchProgressEntity(
                    playlistId = playlistId,
                    kind = if (seriesId == null) KIND_MOVIE else KIND_EPISODE,
                    itemId = itemId,
                    seriesId = seriesId ?: 0,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                    title = title,
                    poster = poster,
                    extension = extension,
                ),
            )
        }
    }

    /** Omiljeni filmovi, odnosno serije, liste; poslednje dodat prvi. */
    fun observeFavorites(playlistId: Long, kind: VodKind): Flow<List<VodItem>> =
        favoriteDao.observe(playlistId, kind.favoriteKind).withoutLocked(kind) { it.itemId }.map { list ->
            list.map { VodItem(id = it.itemId, name = it.name, poster = it.poster, rating = null, extension = it.extension) }
        }

    fun observeIsFavorite(playlistId: Long, kind: VodKind, itemId: Long): Flow<Boolean> =
        favoriteDao.observeIsFavorite(playlistId, kind.favoriteKind, itemId)

    /** Dodaje film ili seriju u omiljene, odnosno uklanja ako je već tamo. */
    suspend fun toggleFavorite(kind: VodKind, item: VodItem, favorite: Boolean) = withContext(Dispatchers.IO) {
        val playlistId = settings.activePlaylistId.first() ?: return@withContext
        if (favorite) {
            favoriteDao.delete(playlistId, kind.favoriteKind, item.id)
        } else {
            favoriteDao.insert(
                VodFavoriteEntity(
                    playlistId = playlistId,
                    kind = kind.favoriteKind,
                    itemId = item.id,
                    name = item.name,
                    poster = item.poster,
                    extension = item.extension,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private val VodKind.favoriteKind: String get() = if (this == VodKind.MOVIES) KIND_MOVIE else KIND_SERIES

    private suspend fun session(): Session {
        val playlistId = settings.activePlaylistId.first() ?: throw VodUnavailableException()
        val playlist = playlistDao.getById(playlistId) ?: throw VodUnavailableException()
        if (playlist.type != PlaylistType.XTREAM) throw VodUnavailableException()
        val server = XtreamUrls.normalizeServer(playlist.source) ?: throw VodUnavailableException()
        val username = playlist.username ?: throw VodUnavailableException()
        val password = try {
            cipher.decrypt(playlist.passwordEncrypted ?: throw VodUnavailableException())
        } catch (e: VodUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Stored password can not be read", e)
        }
        return Session(playlistId, server, username, password, playlist.userAgent)
    }

    /** Spisak jedne kategorije, ili cele liste kad je [categoryId] [ALL_CATEGORIES]. */
    private fun Session.listUrl(action: String, categoryId: String): String =
        if (categoryId == ALL_CATEGORIES) api(action) else api(action, "category_id", categoryId)

    private fun Session.api(action: String): String = XtreamUrls.playerApi(server, username, password, action)

    private fun Session.api(action: String, parameter: String, value: String): String =
        XtreamUrls.playerApi(server, username, password, action, parameter, value)

    /** Ocena kao kratak tekst; "0" i prazno znače da ocene nema. */
    private fun String?.cleanRating(): String? {
        val value = this?.trim()?.toDoubleOrNull() ?: return null
        return if (value <= 0.0) null else ((value * 10).toInt() / 10.0).toString()
    }

    private fun WatchProgressEntity.toDomain() = WatchProgress(itemId, positionMs, durationMs)

    private suspend fun <V : Any> ConcurrentHashMap<String, V>.getOrPutSuspending(key: String, load: suspend () -> V): V {
        this[key]?.let { return it }
        val value = load()
        this[key] = value
        return value
    }

    private companion object {
        const val KIND_MOVIE = "movie"
        const val KIND_EPISODE = "episode"
        const val KIND_SERIES = "series"

        /** Oznaka za spisak bez kategorije (svi filmovi, odnosno sve serije). */
        const val ALL_CATEGORIES = ""

        /** Najviše stavki u redu „Nastavi gledanje”. */
        const val CONTINUE_LIMIT = 20
    }
}
