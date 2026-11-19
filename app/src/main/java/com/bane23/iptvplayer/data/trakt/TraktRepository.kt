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
import kotlinx.coroutines.NonCancellable
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

/** [PAUSED]: automatska sinhronizacija je zaustavljena jer je više puta zaredom ugasila aplikaciju. */
enum class TraktSyncResult { DONE, NOT_SIGNED_IN, FAILED, PAUSED }

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

    /** Automatska sinhronizacija je zaustavljena dok ručna ne uspe (vidi [TraktTokenStore.unfinishedSyncs]). */
    val syncPaused: Flow<Boolean> = tokens.syncPaused

    /** TMDB ID-jevi filmova odgledanih na nalogu. */
    val watchedMovies: Flow<Set<Long>> = dao.observeWatchedMovies().map { it.toSet() }

    /** Odgledane epizode jedne serije na nalogu: par (sezona, epizoda) i kada je poslednji put gledana (ms; 0 kad se ne zna). */
    fun watchedEpisodes(showTmdbId: Long): Flow<Map<Pair<Int, Int>, Long>> =
        dao.observeWatchedEpisodes(showTmdbId).map { rows -> rows.associate { (it.season to it.episode) to it.watchedAt } }

    /**
     * Traži svežu sinhronizaciju ako je poslednja bila poodavno: pri povratku u aplikaciju i pri
     * otvaranju detalja filma ili serije, da se vidi ono što je u međuvremenu gledano na drugom uređaju.
     */
    fun refreshIfStale() {
        if (isAvailable) scheduler.syncIfStale()
    }

    /** Prijava koja je u toku: kod za korisnika i PKCE tajna koja ide uz preuzimanje tokena. */
    class PendingLogin(val code: DeviceCode, internal val verifier: String)

    suspend fun startLogin(): PendingLogin = withContext(Dispatchers.IO) {
        val verifier = Pkce.newVerifier()
        PendingLogin(api.deviceCode(DeviceCodeRequest(CLIENT_ID, Pkce.challenge(verifier))), verifier)
    }

    /** Čeka da korisnik odobri kod na trakt.tv/activate. Prekida se otkazivanjem korutine. */
    suspend fun awaitLogin(login: PendingLogin): TraktLoginResult {
        val (result, token) = awaitToken(login)
        if (token != null) withContext(Dispatchers.IO) { signedIn(token) }
        return result
    }

    /**
     * Kao [awaitLogin], ali prijavu ne čuva ovde nego je vraća: tako telefon dobija posebnu prijavu
     * za TV i šalje je uz liste. Prijava je null osim kad je ishod [TraktLoginResult.SUCCESS].
     */
    suspend fun awaitToken(login: PendingLogin): Pair<TraktLoginResult, TokenResponse?> = withContext(Dispatchers.IO) {
        val code = login.code
        var intervalMs = code.interval.coerceAtLeast(1) * 1000
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            val response = try {
                api.deviceToken(DeviceTokenRequest(code.deviceCode, CLIENT_ID, login.verifier, CLIENT_SECRET))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                continue // Mreža je zastala; kod važi i dalje.
            } catch (_: Exception) {
                return@withContext TraktLoginResult.FAILED to null // Odgovor koji nije Trakt-ov (npr. stranica mreže).
            }
            when (response.code()) {
                200 -> {
                    val token = response.body() ?: return@withContext TraktLoginResult.FAILED to null
                    return@withContext TraktLoginResult.SUCCESS to token
                }
                400 -> Unit // Korisnik još nije odobrio.
                429 -> intervalMs += 1000
                418 -> return@withContext TraktLoginResult.DENIED to null
                404, 409, 410 -> return@withContext TraktLoginResult.EXPIRED to null
                else -> return@withContext TraktLoginResult.FAILED to null
            }
        }
        TraktLoginResult.EXPIRED to null
    }

    /**
     * Prihvata prijavu dobijenu na drugom uređaju (prenos sa telefona). Postojeća prijava se ne
     * dira. Vraća true ako je nalog posle ovoga povezan.
     */
    suspend fun adoptLogin(token: TokenResponse): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext false
        if (!tokens.isSignedIn()) signedIn(token)
        true
    }

    /** Opoziva prijavu koja nije upotrebljena (npr. prenos na TV je otkazan). */
    suspend fun discard(token: TokenResponse) = withContext(Dispatchers.IO) {
        try {
            api.revoke(RevokeRequest(token.accessToken, CLIENT_ID, CLIENT_SECRET))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Neupotrebljena prijava samo ostaje na nalogu dok ne istekne.
        }
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
            dao.clearPlayback()
        }
    }

    /**
     * Šalje promene koje čekaju, pa preuzima sve odgledano sa naloga. Nijedna greška ne izlazi odavde
     * (osim otkazivanja). [automatic] je sinhronizacija u pozadini: preskače se kad je prethodnih
     * nekoliko puta aplikacija ugašena usred sinhronizacije.
     */
    suspend fun sync(automatic: Boolean = false): TraktSyncResult = withContext(Dispatchers.IO) {
        syncLock.withLock {
            if (tokens.session() == null) return@withLock TraktSyncResult.NOT_SIGNED_IN
            if (automatic && tokens.unfinishedSyncs() >= TraktTokenStore.MAX_UNFINISHED_SYNCS) {
                return@withLock TraktSyncResult.PAUSED
            }
            tokens.syncStarted()
            try {
                val auth = authorization() ?: return@withLock TraktSyncResult.NOT_SIGNED_IN
                push(auth)
                pushPlayback(auth)
                pull(auth)
                pullPlayback(auth)
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
            } catch (_: Throwable) {
                // Mreža, Trakt, neočekivan odgovor ili nedostatak memorije: pokušava se ponovo kasnije.
                TraktSyncResult.FAILED
            } finally {
                withContext(NonCancellable) { tokens.syncFinished() }
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
                panel { resolve(change) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PanelUnreadable) {
                null // Kao da TMDB ID-ja nema: promena se odbacuje.
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
        val movies = allPages(MOVIES_PAGE) { page -> api.watchedMovies(auth, page, MOVIES_PAGE) }
        val shows = allPages(SHOWS_PAGE) { page -> api.watchedShows(auth, page, SHOWS_PAGE) }
        val rows: List<TraktWatchedEntity> = TraktPayloads.watchedRows(movies, shows, System.currentTimeMillis())
        db.withTransaction {
            dao.clearWatched()
            dao.insertWatched(rows)
        }
        favoriteStartedShows(shows)
    }

    /**
     * Započete serije sa naloga dodaje u omiljene aktivne liste, svaku samo jednom. Kad spisak
     * serija trenutno nije dostupan, pokušava se pri sledećoj sinhronizaciji.
     */
    private suspend fun favoriteStartedShows(shows: List<WatchedShow>) {
        val playlistId = settings.activePlaylistId.first() ?: return
        val done = tokens.autoFavorites()
        val fresh = TraktPayloads.startedShows(shows).filter { "$playlistId/$it" !in done }
        if (fresh.isEmpty()) return
        val added = try {
            panel { vod.addSeriesFavorites(playlistId, fresh) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return
        }
        if (added.isNotEmpty()) tokens.addAutoFavorites(added.map { "$playlistId/$it" })
    }

    /**
     * Sve strane jednog spiska sa naloga. Staje kad zaglavlje kaže da je strana poslednja, kad strana
     * dođe prazna ili ne donese ništa novo (Trakt za stranu preko kraja ume da ponovi već poslato).
     */
    private suspend fun <T> allPages(limit: Int, load: suspend (page: Int) -> retrofit2.Response<List<T>>): List<T> {
        val all = LinkedHashSet<T>()
        for (page in 1..MAX_PAGES) {
            val response = load(page)
            if (!response.isSuccessful) throw HttpException(response)
            val items = response.body().orEmpty()
            val before = all.size
            all.addAll(items)
            if (items.isEmpty() || all.size == before) break
            val pageCount = response.headers()["X-Pagination-Page-Count"]?.toIntOrNull()
            if (pageCount != null) {
                if (page >= pageCount) break
            } else if (items.size < limit) {
                break
            }
        }
        return all.toList()
    }

    /** Šalje pozicije prekinutog gledanja (za nastavak na drugom uređaju). */
    private suspend fun pushPlayback(auth: String) {
        val activePlaylistId = settings.activePlaylistId.first()
        for (row in dao.pendingPlayback()) {
            if (row.playlistId != activePlaylistId) continue
            val key = try {
                panel { resolve(row.kind, row.itemId, row.seriesId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PanelUnreadable) {
                null
            } catch (_: IOException) {
                continue
            }
            if (key != null) {
                val response = api.scrobblePause(auth, TraktPayloads.scrobble(key, row.progress))
                // 401 i 429 se ponavljaju kasnije; ostale greške (npr. 409, već poslato) se ne ponavljaju.
                if (response.code() == 401 || response.code() == 429 || response.code() >= 500) throw HttpException(response)
            }
            dao.deletePlayback(row.playlistId, row.kind, row.itemId)
        }
    }

    /** Preuzima pozicije sa drugih uređaja koje su novije od poslednje primenjene. */
    private suspend fun pullPlayback(auth: String) {
        val since = tokens.lastPlaybackMs()
        val items = api.playback(auth)
            .mapNotNull { item -> TraktPayloads.parseTime(item.pausedAt)?.let { it to item } }
            .filter { (pausedAt, _) -> pausedAt > since }
            .sortedBy { it.first }
        if (items.isEmpty()) return
        var applied = since
        for ((pausedAt, item) in items) {
            val key = TraktPayloads.playbackKey(item) ?: continue
            // Posle ove pauze je odgledano do kraja (ovde ili drugde): stara pozicija se ne vraća.
            val watchedAt = dao.watchedAt(key.kind, key.tmdbId, key.season, key.episode)
            if (TraktPayloads.finishedSince(watchedAt, pausedAt)) {
                applied = pausedAt
                continue
            }
            try {
                panel { vod.applyRemoteProgress(key, item.progress, pausedAt) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PanelUnreadable) {
                // Ova pozicija se preskače; novije se i dalje primenjuju.
            } catch (_: IOException) {
                // Panel trenutno ne odgovara: ova i novije pozicije čekaju sledeću sinhronizaciju.
                break
            }
            applied = pausedAt
        }
        if (applied > since) tokens.setLastPlaybackMs(applied)
    }

    /**
     * Poziv ka panelu provajdera (spiskovi i detalji filmova i serija). Njegove greške se ovde
     * odvajaju od Trakt-ovih: bez toga bi odgovor panela 401 (npr. istekao nalog kod provajdera)
     * izgledao kao da je Trakt odbio prijavu i odjavio bi korisnika sa Trakt-a.
     *
     * HTTP greška panela postaje [IOException], pa se stavka odlaže do sledeće sinhronizacije. Odgovor
     * koji ne može da se pročita postaje [PanelUnreadable]: on se ni sledeći put neće pročitati, pa se
     * stavka preskače umesto da zauvek prekida sinhronizaciju.
     */
    private suspend fun <T> panel(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: HttpException) {
        throw IOException("Provider answered HTTP ${e.code()}")
    } catch (e: Exception) {
        throw PanelUnreadable(e.javaClass.simpleName)
    }

    private class PanelUnreadable(detail: String) : IOException("Provider answer can not be read: $detail")

    /** TMDB ključ promene, ili null kad ga panel ne šalje. */
    private suspend fun resolve(change: TraktPendingEntity): TraktKey? = resolve(change.kind, change.itemId, change.seriesId)

    private suspend fun resolve(kind: String, itemId: Long, seriesId: Long): TraktKey? =
        if (kind == TraktKey.MOVIE) {
            vod.movieTmdbId(itemId)?.let { TraktKey.movie(it) }
        } else {
            vod.episodeTraktKey(seriesId, itemId)
        }

    /** Zaglavlje sa tokenom; token se osvežava kad je blizu isteka. Null kad prijave nema. */
    private suspend fun authorization(): String? {
        val session = tokens.session() ?: return null
        if (session.expiresAtMs - System.currentTimeMillis() > REFRESH_BEFORE_MS) return bearer(session.accessToken)
        var response = api.refreshToken(RefreshTokenRequest(session.refreshToken, CLIENT_ID, CLIENT_SECRET, LEGACY_REDIRECT))
        if (response.code() == 400) {
            // Aplikacije bez Client Secret-a nemaju ni redirect adresu za prijavu kodom; probati bez nje.
            response = api.refreshToken(RefreshTokenRequest(session.refreshToken, CLIENT_ID, CLIENT_SECRET))
        }
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
        /** Samo starije Trakt aplikacije imaju Client Secret; inače se ne šalje. */
        val CLIENT_SECRET: String? = BuildConfig.TRAKT_CLIENT_SECRET.ifEmpty { null }

        const val LEGACY_REDIRECT = "urn:ietf:wg:oauth:2.0:oob"

        /** Najviše stavki po strani koje Trakt dozvoljava: 250, odnosno 100 kad se traže i epizode. */
        const val MOVIES_PAGE = 250
        const val SHOWS_PAGE = 100

        /** Granica za svaki slučaj, da greška u straničenju ne vrti zahteve unedogled. */
        const val MAX_PAGES = 200

        /** Token se osvežava dan pre isteka. */
        const val REFRESH_BEFORE_MS = 24 * 60 * 60 * 1000L
    }
}
