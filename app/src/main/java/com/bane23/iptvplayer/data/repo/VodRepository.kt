package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    private companion object {
        const val FINISHED_PERCENT = 95
        const val MIN_RESUME_MS = 30_000L
    }
}

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
) {
    private val playlistDao = db.playlistDao()
    private val progressDao = db.watchProgressDao()

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

    suspend fun categories(kind: VodKind): List<VodCategory> = withContext(Dispatchers.IO) {
        val session = session()
        categoryCache.getOrPutSuspending("${session.playlistId}/$kind") {
            val action = if (kind == VodKind.MOVIES) XtreamUrls.ACTION_VOD_CATEGORIES else XtreamUrls.ACTION_SERIES_CATEGORIES
            api.categories(session.api(action), session.userAgent).mapNotNull { category ->
                val id = category.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VodCategory(id, category.name?.takeIf { it.isNotBlank() } ?: id)
            }
        }
    }

    suspend fun items(kind: VodKind, categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val session = session()
        itemCache.getOrPutSuspending("${session.playlistId}/$kind/$categoryId") {
            if (kind == VodKind.MOVIES) {
                api.vodStreams(session.api(XtreamUrls.ACTION_VOD_STREAMS, "category_id", categoryId), session.userAgent)
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
                api.series(session.api(XtreamUrls.ACTION_SERIES, "category_id", categoryId), session.userAgent)
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

    /** Pamti poziciju gledanja. [seriesId] je null za film. Ne čeka da se upis završi. */
    fun saveProgress(itemId: Long, seriesId: Long?, positionMs: Long, durationMs: Long) {
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
                ),
            )
        }
    }

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
    }
}
