package com.bane23.iptvplayer.data.trakt

import androidx.room.withTransaction
import com.bane23.iptvplayer.BuildConfig
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.TraktPendingEntity
import com.bane23.iptvplayer.data.db.TraktWatchedEntity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.work.TraktScheduler
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Kako se završila prijava kodom. */
enum class TraktLoginResult { SUCCESS, EXPIRED, DENIED, FAILED }

enum class TraktSyncResult { DONE, NOT_SIGNED_IN, FAILED }

/**
 * Trakt nalog: prijava kodom (trakt.tv/activate), slanje oznaka „odgledano” i preuzimanje onoga
 * što je na nalogu odgledano. Filmovi i serije se poklapaju preko TMDB ID-ja iz panela; ono što
 * ga nema ne ide na Trakt.
 */
@Singleton
class TraktRepository @Inject constructor(
    private val api: TraktApi,
    private val tokens: TraktTokenStore,
    private val db: AppDatabase,
    private val vod: VodRepository,
    private val settings: SettingsStore,
    private val scheduler: TraktScheduler,
) {
    private val dao = db.traktDao()
    private val progressDao = db.watchProgressDao()
    private val syncLock = Mutex()

    val isAvailable: Boolean get() = tokens.isAvailable

    /** Korisničko ime prijavljenog naloga ("" ako nije poznato), ili null kad prijave nema. */
    val username: Flow<String?> = tokens.username

    val lastSyncMs: Flow<Long?> = tokens.lastSyncMs

    /** TMDB ID-jevi filmova odgledanih na nalogu. */
    val watchedMovies: Flow<Set<Long>> = dao.observeWatchedMovies().map { it.toSet() }

    /** Odgledane epizode jedne serije na nalogu, kao par (sezona, epizoda). */
    fun watchedEpisodes(showTmdbId: Long): Flow<Set<Pair<Int, Int>>> =
        dao.observeWatchedEpisodes(showTmdbId).map { rows -> rows.mapTo(HashSet()) { it.season to it.episode } }

    suspend fun startLogin(): DeviceCode = withContext(Dispatchers.IO) { api.deviceCode(DeviceCodeRequest(CLIENT_ID)) }

    /** Čeka da korisnik odobri kod na trakt.tv/activate. Prekida se otkazivanjem korutine. */
    suspend fun awaitLogin(code: DeviceCode): TraktLoginResult = withContext(Dispatchers.IO) {
        var intervalMs = code.interval.coerceAtLeast(1) * 1000
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            val response = try {
                api.deviceToken(DeviceTokenRequest(code.deviceCode, CLIENT_ID, CLIENT_SECRET))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                continue // Mreža je zastala; kod važi i dalje.
            }
            when (response.code()) {
                200 -> {
                    val token = response.body() ?: return@withContext TraktLoginResult.FAILED
                    signedIn(token)
                    return@withContext TraktLoginResult.SUCCESS
                }
                400 -> Unit // Korisnik još nije odobrio.
                429 -> intervalMs += 1000
                418 -> return@withContext TraktLoginResult.DENIED
                404, 409, 410 -> return@withContext TraktLoginResult.EXPIRED
                else -> return@withContext TraktLoginResult.FAILED
            }
        }
        TraktLoginResult.EXPIRED
    }

    private suspend fun signedIn(token: TokenResponse) {
        tokens.save(token)
        try {
            api.userSettings(bearer(token.accessToken)).user?.let { user ->
                tokens.setUsername(user.username ?: user.name.orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ime naloga je samo za prikaz.
        }
        // Prvo slanje: sve što je u aplikaciji već odgledano ide na nalog.
        val watched = progressDao.allWatched()
        if (watched.isNotEmpty()) {
            val now = System.currentTimeMillis()
            dao.addPending(watched.map { TraktPendingEntity(0, it.playlistId, it.kind, it.itemId, it.seriesId, true, now) })
        }
        scheduler.syncSoon()
        scheduler.schedulePeriodic()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        tokens.session()?.let { session ->
            try {
                api.revoke(RevokeRequest(session.accessToken, CLIENT_ID, CLIENT_SECRET))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Odjava u aplikaciji važi i kad Trakt nije dostupan.
            }
        }
        scheduler.cancel()
        tokens.clear()
        db.withTransaction {
            dao.clearWatched()
            dao.clearPending()
        }
    }

    /** Šalje promene koje čekaju, pa preuzima sve odgledano sa naloga. */
    suspend fun sync(): TraktSyncResult = withContext(Dispatchers.IO) {
        syncLock.withLock {
            try {
                val auth = authorization() ?: return@withLock TraktSyncResult.NOT_SIGNED_IN
                push(auth)
                pull(auth)
                tokens.setLastSync(System.currentTimeMillis())
                TraktSyncResult.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    // Trakt više ne prihvata prijavu (npr. opozvana na sajtu): korisnik se prijavljuje ponovo.
                    tokens.clear()
                    TraktSyncResult.NOT_SIGNED_IN
                } else {
                    TraktSyncResult.FAILED
                }
            } catch (_: IOException) {
                TraktSyncResult.FAILED
            }
        }
    }

    private suspend fun push(auth: String) {
        val pending = dao.pending()
        if (pending.isEmpty()) return
        val activePlaylistId = settings.activePlaylistId.first()
        val adds = ArrayList<TraktKey>()
        val removes = ArrayList<TraktKey>()
        val done = HashSet<List<Long>>()
        for (change in TraktPayloads.latestChanges(pending)) {
            val item = change.itemKey()
            // ID-jevi važe samo za svoju listu; promene druge liste čekaju da ona bude aktivna.
            if (change.playlistId != activePlaylistId) continue
            val key = try {
                resolve(change)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                continue // Panel trenutno ne odgovara; pokušava se pri sledećoj sinhronizaciji.
            }
            if (key != null) (if (change.watched) adds else removes).add(key)
            // Bez TMDB ID-ja Trakt ne može da prepozna stavku, pa se promena odbacuje.
            done.add(item)
        }
        if (adds.isNotEmpty()) {
            api.addHistory(auth, TraktPayloads.history(adds, TraktPayloads.isoTime(System.currentTimeMillis()))).orThrow()
        }
        if (removes.isNotEmpty()) {
            api.removeHistory(auth, TraktPayloads.history(removes, watchedAt = null)).orThrow()
            for (key in removes) dao.deleteWatched(key.kind, key.tmdbId, key.season, key.episode)
        }
        val sent = pending.filter { it.itemKey() in done }.map { it.id }
        if (sent.isNotEmpty()) dao.deletePending(sent)
    }

    private suspend fun pull(auth: String) {
        val movies = api.watchedMovies(auth)
        val shows = api.watchedShows(auth)
        val rows: List<TraktWatchedEntity> = TraktPayloads.watchedRows(movies, shows, System.currentTimeMillis())
        db.withTransaction {
            dao.clearWatched()
            dao.insertWatched(rows)
        }
    }

    /** TMDB ključ promene, ili null kad ga panel ne šalje. */
    private suspend fun resolve(change: TraktPendingEntity): TraktKey? =
        if (change.kind == TraktKey.MOVIE) {
            vod.movieTmdbId(change.itemId)?.let { TraktKey.movie(it) }
        } else {
            vod.episodeTraktKey(change.seriesId, change.itemId)
        }

    /** Zaglavlje sa tokenom; token se osvežava kad je blizu isteka. Null kad prijave nema. */
    private suspend fun authorization(): String? {
        val session = tokens.session() ?: return null
        if (session.expiresAtMs - System.currentTimeMillis() > REFRESH_BEFORE_MS) return bearer(session.accessToken)
        val response = api.refreshToken(RefreshTokenRequest(session.refreshToken, CLIENT_ID, CLIENT_SECRET))
        val token = response.body()
        return when {
            response.isSuccessful && token != null -> {
                tokens.save(token)
                bearer(token.accessToken)
            }
            response.code() == 400 || response.code() == 401 -> {
                tokens.clear()
                null
            }
            else -> throw IOException("Trakt token refresh failed: ${response.code()}")
        }
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun TraktPendingEntity.itemKey() = listOf(playlistId, if (kind == TraktKey.MOVIE) 0L else 1L, itemId, seriesId)

    private fun retrofit2.Response<Unit>.orThrow() {
        if (!isSuccessful) throw HttpException(this)
    }

    private companion object {
        val CLIENT_ID: String = BuildConfig.TRAKT_CLIENT_ID
        val CLIENT_SECRET: String = BuildConfig.TRAKT_CLIENT_SECRET

        /** Token se osvežava dan pre isteka. */
        const val REFRESH_BEFORE_MS = 24 * 60 * 60 * 1000L
    }
}
