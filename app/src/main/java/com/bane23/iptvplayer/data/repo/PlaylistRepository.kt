package com.bane23.iptvplayer.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.CategoryEntity
import com.bane23.iptvplayer.data.db.ChannelEntity
import com.bane23.iptvplayer.data.db.PlaylistEntity
import com.bane23.iptvplayer.data.m3u.M3uItem
import com.bane23.iptvplayer.data.m3u.M3uParser
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.xtream.XtreamApi
import com.bane23.iptvplayer.work.EpgScheduler
import com.bane23.iptvplayer.data.xtream.XtreamJson
import com.bane23.iptvplayer.data.xtream.XtreamLiveStream
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.domain.CategoryType
import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.domain.PlaylistDraft
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistError.Kind
import com.bane23.iptvplayer.domain.PlaylistException
import com.bane23.iptvplayer.domain.PlaylistType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException

/** Jedini ulaz UI sloja za rad sa listama: dodavanje, izmena, osvežavanje, brisanje, aktivna lista. */
@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val httpClient: OkHttpClient,
    private val xtreamApi: XtreamApi,
    private val cipher: PasswordCipher,
    private val epgScheduler: EpgScheduler,
) {
    private val playlistDao = db.playlistDao()
    private val categoryDao = db.categoryDao()
    private val channelDao = db.channelDao()

    /** Liste čije je brisanje u toku. Brisanje velike liste traje, pa se ona odmah sklanja iz prikaza. */
    private val deleting = MutableStateFlow<Set<Long>>(emptySet())

    /** Brisanje ne sme da stane na pola kad korisnik napusti ekran, pa ne zavisi od ekrana. */
    private val deleteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observePlaylists(): Flow<List<Playlist>> =
        combine(playlistDao.observeAllWithCount(), settings.activePlaylistId, deleting) { rows, activeId, gone ->
            rows.filter { it.playlist.id !in gone }.map { row ->
                val p = row.playlist
                Playlist(
                    id = p.id,
                    name = p.name,
                    type = p.type,
                    source = p.source,
                    username = p.username,
                    userAgent = p.userAgent,
                    epgUrl = p.epgUrlOverride,
                    epgShiftHours = p.epgShiftHours,
                    channelCount = row.channelCount,
                    lastRefreshedAt = p.lastRefreshedAt,
                    isActive = p.id == activeId,
                    accountExpiresAt = p.accountExpiresAt,
                    maxConnections = p.maxConnections,
                    merged = p.merged,
                )
            }
        }

    /**
     * Koliko strimova Xtream nalog liste sme da gleda istovremeno. Pita server; vraća null kad lista
     * nije Xtream nalog, kad server ne odgovori ili kad taj podatak ne navodi.
     */
    suspend fun maxConnections(id: Long): Int? = withContext(Dispatchers.IO) {
        try {
            val playlist = playlistDao.getById(id) ?: return@withContext null
            val server = XtreamUrls.normalizeServer(playlist.source) ?: return@withContext null
            val username = playlist.username ?: return@withContext null
            val password = cipher.decrypt(playlist.passwordEncrypted ?: return@withContext null)
            xtreamApi.account(XtreamUrls.playerApi(server, username, password), playlist.userAgent)
                .userInfo?.maxConnections
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPlaylist(id: Long): Playlist? = withContext(Dispatchers.IO) {
        val p = playlistDao.getById(id) ?: return@withContext null
        Playlist(
            id = p.id,
            name = p.name,
            type = p.type,
            source = p.source,
            username = p.username,
            userAgent = p.userAgent,
            epgUrl = p.epgUrlOverride,
            epgShiftHours = p.epgShiftHours,
            channelCount = channelDao.countByPlaylist(id),
            lastRefreshedAt = p.lastRefreshedAt,
            isActive = settings.activePlaylistId.first() == id,
            accountExpiresAt = p.accountExpiresAt,
            maxConnections = p.maxConnections,
        )
    }

    /** Dodaje listu i odmah učitava njen sadržaj. Ako učitavanje ne uspe, lista se ne upisuje. */
    suspend fun addPlaylist(draft: PlaylistDraft): Long = guarded {
        val entity = draft.toNewEntity()
        val id = db.withTransaction {
            val newId = playlistDao.insert(entity)
            importContent(entity.copy(id = newId))
            newId
        }
        if (settings.activePlaylistId.first() == null) settings.setActivePlaylistId(id)
        epgScheduler.refreshNow(id)
        id
    }

    /** Menja podatke liste i ponovo učitava sadržaj. Ako učitavanje ne uspe, ostaje staro stanje. */
    suspend fun updatePlaylist(id: Long, draft: PlaylistDraft) = guarded {
        val existing = playlistDao.getById(id) ?: throw PlaylistException(Kind.UNKNOWN)
        val fresh = draft.toNewEntity(existingPassword = existing.passwordEncrypted)
        val updated = fresh.copy(
            id = id,
            epgUrl = existing.epgUrl,
            lastRefreshedAt = existing.lastRefreshedAt,
            merged = existing.merged,
        )
        db.withTransaction {
            playlistDao.update(updated)
            importContent(updated)
        }
        epgScheduler.refreshNow(id)
    }

    /** Ponovo preuzima sadržaj liste. Ako preuzimanje ne uspe, stari sadržaj ostaje netaknut. */
    suspend fun refreshPlaylist(id: Long) = guarded {
        val entity = playlistDao.getById(id) ?: throw PlaylistException(Kind.UNKNOWN)
        db.withTransaction { importContent(entity) }
        epgScheduler.refreshNow(id)
    }

    /**
     * Automatsko osvežavanje: ponovo preuzima liste koje nisu osvežene duže od [olderThanMs]. Liste
     * iz fajla se preskaču (fajl se ne menja sam). Neuspeh jedne liste ne zaustavlja ostale; njen
     * stari sadržaj ostaje, a greška se vidi tek pri ručnom osvežavanju.
     */
    suspend fun refreshStale(olderThanMs: Long) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        val stale = withContext(Dispatchers.IO) { playlistDao.getAll() }
            .filter { it.type != PlaylistType.M3U_FILE && (it.lastRefreshedAt ?: 0L) < cutoff }
        for (playlist in stale) {
            if (playlist.id in deleting.value) continue
            try {
                refreshPlaylist(playlist.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // sledeći pokušaj je pri narednom redovnom osvežavanju
            }
        }
    }

    /**
     * Lista nestaje iz prikaza odmah, a podaci (kanali, vodič, filmovi) se brišu u pozadini. Drugi
     * poziv za istu listu dok brisanje traje ne radi ništa.
     */
    suspend fun deletePlaylist(id: Long) {
        val started = deleting.getAndUpdate { it + id }
        if (id in started) return
        deleteScope.launch {
            try {
                val entity = playlistDao.getById(id)
                // Aktivna lista se menja pre brisanja, da ostatak aplikacije ne čeka na obrisanu.
                if (settings.activePlaylistId.first() == id) {
                    val next = playlistDao.getAll()
                        .filter { it.id !in deleting.value }
                        .minByOrNull { it.name.lowercase() }
                    settings.setActivePlaylistId(next?.id)
                }
                playlistDao.deleteById(id)
                settings.clearPlaylistCategoryPrefs(id)
                if (entity?.type == PlaylistType.M3U_FILE) releaseFilePermission(entity.source)
            } finally {
                deleting.update { it - id }
            }
        }.join()
    }

    suspend fun setActivePlaylist(id: Long) = settings.setActivePlaylistId(id)

    /**
     * Uključuje listu u spojeni prikaz, odnosno isključuje je. Kad je aktivna lista spojena, kanali
     * svih spojenih lista se u Uživo, vodiču i pretrazi prikazuju zajedno.
     */
    suspend fun setMerged(id: Long, merged: Boolean) = withContext(Dispatchers.IO) { playlistDao.setMerged(id, merged) }

    // ---------------------------------------------------------------------------------------

    private fun PlaylistDraft.toNewEntity(existingPassword: String? = null): PlaylistEntity {
        val cleanName = name.trim()
        val cleanSource = source.trim()
        if (cleanName.isEmpty() || cleanSource.isEmpty()) throw PlaylistException(Kind.MISSING_FIELDS)
        val entity = when (type) {
            PlaylistType.M3U_URL -> {
                val url = cleanSource.toHttpUrlOrNull() ?: throw PlaylistException(Kind.INVALID_URL)
                val xtream = XtreamUrls.parseGetPhpLink(cleanSource)
                if (xtream != null) {
                    // Link je u stvari Xtream nalog: dodaje se kao Xtream lista.
                    PlaylistEntity(
                        name = cleanName,
                        type = PlaylistType.XTREAM,
                        source = xtream.server.toString(),
                        username = xtream.username,
                        passwordEncrypted = cipher.encrypt(xtream.password),
                    )
                } else {
                    PlaylistEntity(name = cleanName, type = type, source = url.toString())
                }
            }

            PlaylistType.M3U_FILE -> PlaylistEntity(name = cleanName, type = type, source = cleanSource)

            PlaylistType.XTREAM -> {
                val server = XtreamUrls.normalizeServer(cleanSource) ?: throw PlaylistException(Kind.INVALID_URL)
                val user = username.trim()
                val encrypted = when {
                    password.isNotEmpty() -> cipher.encrypt(password)
                    else -> existingPassword
                }
                if (user.isEmpty() || encrypted == null) throw PlaylistException(Kind.MISSING_FIELDS)
                PlaylistEntity(
                    name = cleanName,
                    type = type,
                    source = server.toString(),
                    username = user,
                    passwordEncrypted = encrypted,
                )
            }
        }
        val epg = epgUrl.trim()
        if (epg.isNotEmpty() && epg.toHttpUrlOrNull() == null) throw PlaylistException(Kind.INVALID_URL)
        return entity.copy(
            userAgent = userAgent.trim().takeIf { it.isNotEmpty() },
            epgUrlOverride = epg.takeIf { it.isNotEmpty() },
            epgShiftHours = epgShiftHours.coerceIn(-MAX_EPG_SHIFT_HOURS, MAX_EPG_SHIFT_HOURS),
        )
    }

    /** Podešavanja kategorija liste koja se upravo osvežava (naziv -> stara kategorija). */
    private var categoryPrefs: Map<String, CategoryEntity> = emptyMap()

    /** Briše stari sadržaj liste i upisuje novi. Poziva se unutar transakcije. */
    private suspend fun importContent(playlist: PlaylistEntity) {
        // Sakrivene kategorije i njihov redosled se pamte po nazivu, da prežive osvežavanje.
        categoryPrefs = categoryDao.getByPlaylist(playlist.id)
            .filter { it.hidden || it.userOrder != null }
            .associateBy { it.name }
        channelDao.deleteByPlaylist(playlist.id)
        categoryDao.deleteByPlaylist(playlist.id)
        val epgUrl = when (playlist.type) {
            PlaylistType.M3U_URL -> {
                val (workingUrl, stream) = openUrlWithHttpFallback(playlist.source, playlist.userAgent)
                if (workingUrl != playlist.source) playlistDao.update(playlist.copy(source = workingUrl))
                stream.use { importM3u(playlist.id, it) }
            }
            PlaylistType.M3U_FILE -> openFile(playlist.source).use { importM3u(playlist.id, it) }
            PlaylistType.XTREAM -> {
                importXtream(playlist)
                null
            }
        }
        // Ručno povezani kanali odmah dobijaju svoj kanal iz vodiča, ne tek posle učitavanja vodiča.
        channelDao.applyEpgLinks(playlist.id)
        playlistDao.markRefreshed(playlist.id, System.currentTimeMillis(), epgUrl ?: playlist.epgUrl)
    }

    /**
     * Mnogi IPTV serveri rade samo preko običnog HTTP-a, iako korisnik dobije link sa `https`.
     * Ako https veza ne može ni da se uspostavi (odbijena ili neuspeo TLS), vraća istu adresu
     * sa `http`; inače null. Port koji je izričito naveden se zadržava.
     */
    private fun httpFallback(url: HttpUrl, error: IOException): HttpUrl? {
        val connectionLevel = error is ConnectException || error is SSLException
        return if (url.isHttps && connectionLevel) url.newBuilder().scheme("http").build() else null
    }

    /** Otvara link; ako https ne radi, proba http. Vraća adresu koja je radila i sadržaj. */
    private fun openUrlWithHttpFallback(url: String, userAgent: String?): Pair<String, InputStream> {
        return try {
            url to openUrl(url, userAgent)
        } catch (e: IOException) {
            val http = url.toHttpUrlOrNull()?.let { httpFallback(it, e) } ?: throw e
            val stream = try {
                openUrl(http.toString(), userAgent)
            } catch (second: IOException) {
                e.addSuppressed(second)
                throw e
            }
            http.toString() to stream
        }
    }

    private fun openUrl(url: String, userAgent: String?): InputStream {
        val request = Request.Builder().url(url)
            .apply { if (userAgent != null) header("User-Agent", userAgent) }
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw PlaylistException(PlaylistError(Kind.HTTP_ERROR, code))
        }
        return response.body.byteStream()
    }

    private fun openFile(uri: String): InputStream = try {
        context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw PlaylistException(Kind.FILE_UNREADABLE)
    } catch (e: FileNotFoundException) {
        throw PlaylistException(Kind.FILE_UNREADABLE, e)
    } catch (e: SecurityException) {
        throw PlaylistException(Kind.FILE_UNREADABLE, e)
    }

    private fun releaseFilePermission(uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // dozvola nije ni postojala
        }
    }

    /** Vraća EPG link iz zaglavlja liste, ako postoji. */
    private suspend fun importM3u(playlistId: Long, input: InputStream): String? {
        var epgUrl: String? = null
        val writer = ChannelWriter(playlistId)
        try {
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (item in M3uParser.parse(lines)) {
                    when (item) {
                        is M3uItem.Header -> epgUrl = item.epgUrl
                        is M3uItem.Entry -> writer.add(
                            categoryKey = item.group,
                            categoryName = item.group,
                        ) { categoryId, order ->
                            ChannelEntity(
                                playlistId = playlistId,
                                categoryId = categoryId,
                                name = item.name,
                                logo = item.logo,
                                streamUrl = item.url,
                                stableKey = item.url,
                                tvgId = item.tvgId,
                                epgKey = item.tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                                tvgName = item.tvgName,
                                number = item.number,
                                catchupType = item.catchupType,
                                catchupSource = item.catchupSource,
                                catchupDays = item.catchupDays,
                                userAgent = item.userAgent,
                                referrer = item.referrer,
                                sortOrder = order,
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // Neki serveri prekinu vezu bez urednog kraja odgovora. Ako su kanali već pročitani,
            // lista se prihvata; inače je to prava greška.
            val truncated = e is ProtocolException || e is EOFException
            if (!truncated || writer.total == 0) throw e
        }
        writer.flush()
        if (writer.total == 0) throw PlaylistException(Kind.EMPTY_PLAYLIST)
        return epgUrl
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun importXtream(playlist: PlaylistEntity) {
        var server: HttpUrl = XtreamUrls.normalizeServer(playlist.source)
            ?: throw PlaylistException(Kind.INVALID_URL)
        val username = playlist.username ?: throw PlaylistException(Kind.MISSING_FIELDS)
        val password = try {
            cipher.decrypt(playlist.passwordEncrypted ?: throw PlaylistException(Kind.MISSING_FIELDS))
        } catch (e: PlaylistException) {
            throw e
        } catch (e: Exception) {
            throw PlaylistException(Kind.CREDENTIALS_UNREADABLE, e)
        }

        val account = try {
            fetchAccount(server, username, password, playlist.userAgent)
        } catch (e: IOException) {
            // https ne radi: probaj http i, ako uspe, zapamti tu adresu za ubuduće.
            val http = httpFallback(server, e) ?: throw e
            val result = try {
                fetchAccount(http, username, password, playlist.userAgent)
            } catch (second: IOException) {
                e.addSuppressed(second)
                throw e
            }
            server = http
            playlistDao.update(playlist.copy(source = http.toString()))
            result
        }
        val user = account.userInfo
        if (user == null || !user.isAuthenticated) throw PlaylistException(Kind.AUTH_FAILED)
        val status = user.status?.lowercase()
        val expiresAt = user.expiresAtSeconds
        when {
            status == "expired" -> throw PlaylistException(Kind.ACCOUNT_EXPIRED)
            expiresAt != null && expiresAt * 1000 < System.currentTimeMillis() ->
                throw PlaylistException(Kind.ACCOUNT_EXPIRED)
            status == "banned" || status == "disabled" -> throw PlaylistException(Kind.ACCOUNT_DISABLED)
        }
        // Posebnim upitom, da ga kasniji upisi cele liste (izmena, prelazak na http) ne pregaze starim.
        playlistDao.setAccountInfo(playlist.id, expiresAt?.let { it * 1000 }, user.maxConnections)

        val writer = ChannelWriter(playlist.id)
        val categories = xtreamApi.liveCategories(
            XtreamUrls.playerApi(server, username, password, XtreamUrls.ACTION_LIVE_CATEGORIES),
            playlist.userAgent,
        )
        for (category in categories) {
            val key = category.id ?: continue
            writer.ensureCategory(key, category.name?.takeIf { it.isNotBlank() } ?: key)
        }

        val body = xtreamApi.liveStreams(
            XtreamUrls.playerApi(server, username, password, XtreamUrls.ACTION_LIVE_STREAMS),
            playlist.userAgent,
        )
        body.use {
            val streams = XtreamJson.decodeToSequence(
                it.byteStream(),
                XtreamLiveStream.serializer(),
                DecodeSequenceMode.ARRAY_WRAPPED,
            )
            for (stream in streams) {
                val streamId = stream.streamId?.toLongOrNull() ?: continue
                val archiveDays = stream.tvArchiveDuration?.toIntOrNull()
                val hasArchive = stream.tvArchive == "1" && archiveDays != null && archiveDays > 0
                writer.add(categoryKey = stream.categoryId, categoryName = stream.categoryId) { categoryId, order ->
                    ChannelEntity(
                        playlistId = playlist.id,
                        categoryId = categoryId,
                        name = stream.name?.takeIf { n -> n.isNotBlank() } ?: streamId.toString(),
                        logo = stream.icon?.takeIf { i -> i.isNotBlank() },
                        xtreamStreamId = streamId,
                        stableKey = "xtream:$streamId",
                        tvgId = stream.epgChannelId?.takeIf { e -> e.isNotBlank() },
                        epgKey = stream.epgChannelId?.trim()?.lowercase()?.takeIf { e -> e.isNotEmpty() },
                        number = stream.num?.toIntOrNull(),
                        catchupType = if (hasArchive) "xtream" else null,
                        catchupDays = if (hasArchive) archiveDays else null,
                        sortOrder = order,
                    )
                }
            }
        }
        writer.flush()
    }

    private suspend fun fetchAccount(
        server: HttpUrl,
        username: String,
        password: String,
        userAgent: String?,
    ) = try {
        xtreamApi.account(XtreamUrls.playerApi(server, username, password), userAgent)
    } catch (e: HttpException) {
        // Paneli za pogrešnu lozinku često vraćaju 401/403/404 umesto JSON odgovora.
        if (e.code() in setOf(401, 403, 404)) throw PlaylistException(Kind.AUTH_FAILED, e) else throw e
    }

    /** Upisuje kanale u paketima i usput pravi kategorije po redosledu pojavljivanja. */
    private inner class ChannelWriter(private val playlistId: Long) {
        private val categoryIds = HashMap<String, Long>()
        private val batch = ArrayList<ChannelEntity>(BATCH_SIZE)
        var total = 0
            private set

        suspend fun ensureCategory(key: String, name: String): Long {
            categoryIds[key]?.let { return it }
            val id = categoryDao.insert(
                CategoryEntity(
                    playlistId = playlistId,
                    name = name,
                    type = CategoryType.LIVE,
                    sortOrder = categoryIds.size,
                    hidden = categoryPrefs[name]?.hidden ?: false,
                    userOrder = categoryPrefs[name]?.userOrder,
                ),
            )
            categoryIds[key] = id
            return id
        }

        suspend fun add(
            categoryKey: String?,
            categoryName: String?,
            build: (categoryId: Long?, order: Int) -> ChannelEntity,
        ) {
            val key = categoryKey?.takeIf { it.isNotBlank() }
            val categoryId = key?.let { ensureCategory(it, categoryName?.takeIf { n -> n.isNotBlank() } ?: it) }
            batch.add(build(categoryId, total))
            total++
            if (batch.size >= BATCH_SIZE) flush()
        }

        suspend fun flush() {
            if (batch.isEmpty()) return
            channelDao.insertAll(ArrayList(batch))
            batch.clear()
        }
    }

    /** Izvršava posao na IO niti i svaku grešku prevodi u [PlaylistException]. */
    private suspend fun <T> guarded(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaylistException) {
            throw e
        } catch (e: HttpException) {
            throw PlaylistException(PlaylistError(Kind.HTTP_ERROR, e.code()), e)
        } catch (e: SocketTimeoutException) {
            throw PlaylistException(Kind.TIMEOUT, e)
        } catch (e: UnknownHostException) {
            throw PlaylistException(Kind.HOST_NOT_FOUND, e)
        } catch (e: SSLException) {
            throw PlaylistException(Kind.SSL_ERROR, e)
        } catch (e: SerializationException) {
            throw PlaylistException(Kind.BAD_RESPONSE, e)
        } catch (e: IOException) {
            throw PlaylistException(Kind.NO_NETWORK, e)
        } catch (e: Exception) {
            throw PlaylistException(Kind.UNKNOWN, e)
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
        const val MAX_EPG_SHIFT_HOURS = 14
    }
}
