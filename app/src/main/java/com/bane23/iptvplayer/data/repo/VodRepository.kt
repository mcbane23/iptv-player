package com.bane23.iptvplayer.data.repo

import androidx.room.withTransaction
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.VodFavoriteEntity
import com.bane23.iptvplayer.data.db.WatchProgressEntity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.trakt.TraktKey
import com.bane23.iptvplayer.data.trakt.TraktQueue
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.data.xtream.XtreamApi
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.domain.CategoryArrange
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

enum class VodKind { MOVIES, SERIES }

data class VodCategory(val id: String, val name: String)

/** Sakrivene kategorije (ID-jevi) i redosled koji je zadao korisnik. */
data class VodCategoryPrefs(val hidden: Set<String> = emptySet(), val order: List<String> = emptyList())

/** Film ili serija u spisku. */
data class VodItem(
    val id: Long,
    val name: String,
    val poster: String?,
    val rating: String?,
    /** Nastavak fajla filma; za serije null. */
    val extension: String?,
    /** ID na TMDB-u (za Trakt), ako ga panel šalje. */
    val tmdbId: Long? = null,
)

/**
 * Zapamćena pozicija gledanja filma ili epizode. [watched] je oznaka „odgledano”: postavi se sama
 * kad se pogleda do kraja ([FINISHED_PERCENT]) ili je korisnik postavi ručno.
 */
data class WatchProgress(val itemId: Long, val positionMs: Long, val durationMs: Long, val watched: Boolean = false) {
    /** Odgledano: kvačica i, kod serija, nastavak od sledeće epizode. */
    val finished: Boolean get() = watched

    /** Vredi ponuditi nastavak: gledanje je počelo, a nije stiglo do kraja (i kad se odgledano gleda ponovo). */
    val resumable: Boolean get() = positionMs >= MIN_RESUME_MS && !reachedEnd(positionMs, durationMs)

    companion object {
        const val FINISHED_PERCENT = 95
        const val MIN_RESUME_MS = 30_000L

        /** Da li je pozicija stigla do kraja (95%), posle čega se film ili epizoda smatra odgledanim. */
        fun reachedEnd(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs * 100 >= durationMs * FINISHED_PERCENT
    }
}

/** Film ili epizoda koja se ručno označava kao odgledana. [title] i [poster] su filma, odnosno serije. */
data class WatchTarget(val itemId: Long, val title: String?, val poster: String?, val extension: String? = null)

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
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val api: XtreamApi,
    private val cipher: PasswordCipher,
    private val locks: LockRepository,
    private val trakt: TraktQueue,
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

    /**
     * Kategorije za prikaz: bez sakrivenih, redom koji je zadao korisnik. Kategorija koja je sada
     * zaključana ima katanac ispred naziva.
     */
    suspend fun categories(kind: VodKind): List<VodCategory> {
        val all = allCategories(kind)
        val playlistId = settings.activePlaylistId.first() ?: return all
        val prefs = observeCategoryPrefs(playlistId, kind).first()
        val visible = CategoryArrange.ordered(all.filter { it.id !in prefs.hidden }, prefs.order) { it.id }
        val keys = locks.activeKeysNow()
        if (keys.isEmpty()) return visible
        return visible.map { category ->
            val locked = LockRepository.vodKey(playlistId, kind == VodKind.SERIES, category.id) in keys
            if (locked) category.copy(name = LockRepository.LOCK_MARK + category.name) else category
        }
    }

    /** Šta je korisnik sakrio i kako je poređao kategorije filmova, odnosno serija, liste [playlistId]. */
    fun observeCategoryPrefs(playlistId: Long, kind: VodKind): Flow<VodCategoryPrefs> {
        val series = kind == VodKind.SERIES
        val prefix = LockRepository.vodKey(playlistId, series, "")
        return combine(settings.hiddenVodCategories, settings.vodCategoryOrder(playlistId, series)) { hidden, order ->
            VodCategoryPrefs(
                hidden = hidden.filter { it.startsWith(prefix) }.mapTo(HashSet()) { it.removePrefix(prefix) },
                order = order,
            )
        }.distinctUntilChanged()
    }

    /** Isto, za aktivnu listu (prati i promenu liste). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveCategoryPrefs(kind: VodKind): Flow<VodCategoryPrefs> =
        settings.activePlaylistId.flatMapLatest { playlistId ->
            if (playlistId == null) flowOf(VodCategoryPrefs()) else observeCategoryPrefs(playlistId, kind)
        }

    /** Sakriva ili vraća kategorije (ID-jevi sa servera) aktivne liste. */
    suspend fun setCategoriesHidden(kind: VodKind, categoryIds: Collection<String>, hidden: Boolean) {
        val playlistId = settings.activePlaylistId.first() ?: return
        val series = kind == VodKind.SERIES
        settings.setVodCategoriesHidden(categoryIds.map { LockRepository.vodKey(playlistId, series, it) }, hidden)
    }

    /** Pamti redosled kategorija aktivne liste; [categoryIds] su sve kategorije, redom prikaza. */
    suspend fun setCategoryOrder(kind: VodKind, categoryIds: List<String>) {
        val playlistId = settings.activePlaylistId.first() ?: return
        settings.setVodCategoryOrder(playlistId, kind == VodKind.SERIES, categoryIds)
    }

    /** Prebacuje kategoriju na vrh spiska. */
    suspend fun moveCategoryToTop(kind: VodKind, categoryId: String) {
        val playlistId = settings.activePlaylistId.first() ?: return
        val prefs = observeCategoryPrefs(playlistId, kind).first()
        val ids = CategoryArrange.ordered(allCategories(kind), prefs.order) { it.id }.map { it.id }
        val index = ids.indexOf(categoryId)
        if (index > 0) setCategoryOrder(kind, CategoryArrange.movedToTop(ids, index))
    }

    /** Šta je u sekciji bilo otvoreno poslednji put (ID kategorije ili posebna oznaka), ili null. */
    suspend fun lastCategory(kind: VodKind): String? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return settings.lastCategory(playlistId, kind.section).first()
    }

    suspend fun setLastCategory(kind: VodKind, value: String) {
        val playlistId = settings.activePlaylistId.first() ?: return
        settings.setLastCategory(playlistId, kind.section, value)
    }

    private val VodKind.section get() = if (this == VodKind.SERIES) "series" else "movies"

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
                            tmdbId = XtreamVodParser.tmdbId(stream.tmdb) ?: XtreamVodParser.tmdbId(stream.tmdbId),
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
                            tmdbId = XtreamVodParser.tmdbId(series.tmdb) ?: XtreamVodParser.tmdbId(series.tmdbId),
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
        stopped: Boolean = false,
    ) {
        if (durationMs <= 0) return
        writeScope.launch {
            val playlistId = settings.activePlaylistId.first() ?: return@launch
            val kind = if (seriesId == null) KIND_MOVIE else KIND_EPISODE
            val newlyWatched = db.withTransaction {
                val now = System.currentTimeMillis()
                // Oznaka „odgledano” ostaje i kad se film gleda ponovo; nova se dobija gledanjem do kraja.
                val previous = progressDao.get(playlistId, kind, itemId)?.watchedAt
                val watchedAt = previous ?: now.takeIf { WatchProgress.reachedEnd(positionMs, durationMs) }
                progressDao.upsert(
                    WatchProgressEntity(
                        playlistId = playlistId,
                        kind = kind,
                        itemId = itemId,
                        seriesId = seriesId ?: 0,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        updatedAt = now,
                        title = title,
                        poster = poster,
                        extension = extension,
                        watchedAt = watchedAt,
                    ),
                )
                previous == null && watchedAt != null
            }
            if (newlyWatched) trakt.changed(playlistId, kind, listOf(itemId), seriesId ?: 0, watched = true)
            // Prekinuto pre kraja: pozicija ide na Trakt, da se nastavi na drugom uređaju.
            if (stopped && positionMs >= WatchProgress.MIN_RESUME_MS && !WatchProgress.reachedEnd(positionMs, durationMs)) {
                val percent = (positionMs * 100f / durationMs).coerceIn(0f, 100f)
                trakt.playbackChanged(playlistId, kind, itemId, seriesId ?: 0, percent)
            }
        }
    }

    /**
     * Pozicija sa Trakt naloga (gledano na drugom uređaju): [progress] je procenat, [pausedAtMs]
     * vreme prekida. Upisuje se samo ako je novija od ovde zapamćene i stvarno drugačija.
     * Film, odnosno serija, traži se po TMDB ID-ju u spisku aktivne liste.
     */
    suspend fun applyRemoteProgress(key: TraktKey, progress: Float, pausedAtMs: Long) = withContext(Dispatchers.IO) {
        val playlistId = session().playlistId
        val isMovie = key.kind == TraktKey.MOVIE
        val target: RemoteTarget = if (isMovie) {
            val item = allItems(VodKind.MOVIES).firstOrNull { it.tmdbId == key.tmdbId } ?: return@withContext
            RemoteTarget(item.id, null, item.name, item.poster, item.extension) {
                movie(item.id).durationSeconds
            }
        } else {
            val show = allItems(VodKind.SERIES).firstOrNull { it.tmdbId == key.tmdbId } ?: return@withContext
            val details = series(show.id)
            val episode = details.episodes.firstOrNull { it.season == key.season && it.number == key.episode }
                ?: return@withContext
            RemoteTarget(episode.id, show.id, details.name ?: show.name, show.poster ?: details.poster, null) {
                episode.durationSeconds
            }
        }
        val kind = if (isMovie) KIND_MOVIE else KIND_EPISODE
        val existing = progressDao.get(playlistId, kind, target.itemId)
        if (existing != null && existing.updatedAt >= pausedAtMs) return@withContext
        val durationMs = existing?.durationMs?.takeIf { it > 0 } ?: target.durationSeconds()?.times(1000) ?: return@withContext
        val positionMs = (durationMs * progress / 100).toLong()
        if (existing != null && kotlin.math.abs(existing.positionMs - positionMs) < SAME_POSITION_MS) return@withContext
        progressDao.upsert(
            WatchProgressEntity(
                playlistId = playlistId,
                kind = kind,
                itemId = target.itemId,
                seriesId = target.seriesId ?: 0,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = pausedAtMs,
                title = target.title,
                poster = target.poster,
                extension = target.extension,
                watchedAt = existing?.watchedAt,
            ),
        )
    }

    private class RemoteTarget(
        val itemId: Long,
        val seriesId: Long?,
        val title: String?,
        val poster: String?,
        val extension: String?,
        val durationSeconds: suspend () -> Long?,
    )

    /**
     * Ručno označava filmove, odnosno epizode jedne serije ([seriesId] null za film), kao odgledane
     * ili poništava oznaku. Epizode idu redom kojim se gledaju, da bi nastavak serije krenuo posle
     * poslednje označene. [traktKeys] su isti filmovi, odnosno epizode, kako ih zna Trakt: kad se oznaka
     * poništava, odmah se skida i ona preuzeta sa Trakt-a.
     */
    suspend fun setWatched(
        seriesId: Long?,
        targets: List<WatchTarget>,
        watched: Boolean,
        traktKeys: List<TraktKey> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext
        val playlistId = settings.activePlaylistId.first() ?: return@withContext
        val kind = if (seriesId == null) KIND_MOVIE else KIND_EPISODE
        val ids = targets.map { it.itemId }
        db.withTransaction {
            val existing = progressDao.getMany(playlistId, kind, ids).associateBy { it.itemId }
            if (watched) {
                val now = System.currentTimeMillis()
                val rows = targets.mapIndexed { index, target ->
                    existing[target.itemId]?.let { row ->
                        // Već odgledano ostaje kako jeste. Započeto se računa kao pogledano do kraja,
                        // da ne bi nudilo nastavak i da bi serija krenula od sledeće epizode.
                        if (row.watchedAt != null) row else row.copy(watchedAt = now, positionMs = row.durationMs)
                    } ?: WatchProgressEntity(
                        playlistId = playlistId,
                        kind = kind,
                        itemId = target.itemId,
                        seriesId = seriesId ?: 0,
                        positionMs = 0,
                        durationMs = 0,
                        // Nikad gledano: vreme je redni broj, pa je ovo starije od svega stvarno gledanog,
                        // a među označenim epizodama poslednja je ona koja je poslednja i po redu.
                        updatedAt = index.toLong(),
                        // Bez naziva, da ručno označeno ne upadne u red „Nastavi gledanje”.
                        title = null,
                        poster = null,
                        extension = target.extension,
                        watchedAt = now,
                    )
                }
                progressDao.upsertAll(rows)
            } else {
                // Ono što nikad nije ni puštano briše se; inače se skida oznaka, a gledanje do kraja
                // vraća na početak, da film ne bi odmah ponovo bio odgledan.
                val (neverPlayed, played) = existing.values.partition { it.durationMs <= 0 }
                if (neverPlayed.isNotEmpty()) progressDao.deleteMany(playlistId, kind, neverPlayed.map { it.itemId })
                if (played.isNotEmpty()) progressDao.upsertAll(
                    played.map { row ->
                        val position = if (WatchProgress.reachedEnd(row.positionMs, row.durationMs)) 0 else row.positionMs
                        row.copy(watchedAt = null, positionMs = position)
                    },
                )
            }
        }
        if (!watched) trakt.forget(traktKeys)
        trakt.changed(playlistId, kind, ids, seriesId ?: 0, watched)
    }

    /** TMDB ID filma (za Trakt): iz već učitanih spiskova, inače iz detalja filma ili celog spiska. Null ako ga panel ne šalje. */
    suspend fun movieTmdbId(movieId: Long): Long? =
        cachedListTmdbId(VodKind.MOVIES, movieId) ?: movie(movieId).tmdbId ?: movieListTmdbId(movieId)

    /** TMDB ID filma iz spiskova (neki paneli ga šalju samo tamo). */
    suspend fun movieListTmdbId(movieId: Long): Long? = listTmdbId(VodKind.MOVIES, movieId)

    /** Epizoda kako je zna Trakt: TMDB ID serije, sezona i broj. Null ako panel ne šalje TMDB ID serije. */
    suspend fun episodeTraktKey(seriesId: Long, episodeId: Long): TraktKey? {
        val details = series(seriesId)
        val tmdb = details.tmdbId ?: seriesListTmdbId(seriesId) ?: return null
        val episode = details.episodes.firstOrNull { it.id == episodeId } ?: return null
        return TraktKey.episode(tmdb, episode.season, episode.number)
    }

    /** TMDB ID serije iz spiskova (neki paneli ga šalju samo tamo). */
    suspend fun seriesListTmdbId(seriesId: Long): Long? = listTmdbId(VodKind.SERIES, seriesId)

    /**
     * TMDB ID iz spiskova: prvo iz onih koji su već u memoriji, inače se učitava ceo spisak. Bez toga
     * serija otvorena pre nego što je ijedan spisak učitan (npr. iz reda „Nastavi gledanje”) ne bi
     * imala TMDB ID, pa ni oznake sa Trakt-a.
     */
    private suspend fun listTmdbId(kind: VodKind, itemId: Long): Long? =
        cachedListTmdbId(kind, itemId) ?: loadItems(kind, ALL_CATEGORIES).firstOrNull { it.id == itemId }?.tmdbId

    private suspend fun cachedListTmdbId(kind: VodKind, itemId: Long): Long? {
        val prefix = "${session().playlistId}/$kind/"
        return itemCache.entries.asSequence()
            .filter { it.key.startsWith(prefix) }
            .firstNotNullOfOrNull { (_, list) -> list.firstOrNull { it.id == itemId }?.tmdbId }
    }

    /** ID-jevi odgledanih filmova aktivne liste, za kvačicu na posteru. */
    fun observeWatchedMovieIds(playlistId: Long): Flow<Set<Long>> =
        progressDao.observeWatchedIds(playlistId, KIND_MOVIE).map { it.toSet() }

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

    private fun WatchProgressEntity.toDomain() = WatchProgress(itemId, positionMs, durationMs, watched = watchedAt != null)

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

        /** Razlika pozicija za koju se ne menja zapamćeno (zaokruživanje procenta na Trakt-u). */
        const val SAME_POSITION_MS = 30_000L

        /** Najviše stavki u redu „Nastavi gledanje”. */
        const val CONTINUE_LIMIT = 20
    }
}
