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
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
) {
    private val playlistDao = db.playlistDao()
    private val categoryDao = db.categoryDao()
    private val channelDao = db.channelDao()

    fun observePlaylists(): Flow<List<Playlist>> =
        combine(playlistDao.observeAllWithCount(), settings.activePlaylistId) { rows, activeId ->
            rows.map { row ->
                val p = row.playlist
                Playlist(
                    id = p.id,
                    name = p.name,
                    type = p.type,
                    source = p.source,
                    username = p.username,
                    channelCount = row.channelCount,
                    lastRefreshedAt = p.lastRefreshedAt,
                    isActive = p.id == activeId,
                )
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
            channelCount = channelDao.countByPlaylist(id),
            lastRefreshedAt = p.lastRefreshedAt,
            isActive = settings.activePlaylistId.first() == id,
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
        id
    }

    /** Menja podatke liste i ponovo učitava sadržaj. Ako učitavanje ne uspe, ostaje staro stanje. */
    suspend fun updatePlaylist(id: Long, draft: PlaylistDraft) = guarded {
        val existing = playlistDao.getById(id) ?: throw PlaylistException(Kind.UNKNOWN)
        val fresh = draft.toNewEntity(existingPassword = existing.passwordEncrypted)
        val updated = fresh.copy(id = id, epgUrl = existing.epgUrl, lastRefreshedAt = existing.lastRefreshedAt)
        db.withTransaction {
            playlistDao.update(updated)
            importContent(updated)
        }
    }

    /** Ponovo preuzima sadržaj liste. Ako preuzimanje ne uspe, stari sadržaj ostaje netaknut. */
    suspend fun refreshPlaylist(id: Long) = guarded {
        val entity = playlistDao.getById(id) ?: throw PlaylistException(Kind.UNKNOWN)
        db.withTransaction { importContent(entity) }
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(id)
        playlistDao.deleteById(id)
        if (entity?.type == PlaylistType.M3U_FILE) releaseFilePermission(entity.source)
        if (settings.activePlaylistId.first() == id) {
            settings.setActivePlaylistId(playlistDao.firstId())
        }
    }

    suspend fun setActivePlaylist(id: Long) = settings.setActivePlaylistId(id)

    // ---------------------------------------------------------------------------------------

    private fun PlaylistDraft.toNewEntity(existingPassword: String? = null): PlaylistEntity {
        val cleanName = name.trim()
        val cleanSource = source.trim()
        if (cleanName.isEmpty() || cleanSource.isEmpty()) throw PlaylistException(Kind.MISSING_FIELDS)
        return when (type) {
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
    }

    /** Briše stari sadržaj liste i upisuje novi. Poziva se unutar transakcije. */
    private suspend fun importContent(playlist: PlaylistEntity) {
        channelDao.deleteByPlaylist(playlist.id)
        categoryDao.deleteByPlaylist(playlist.id)
        val epgUrl = when (playlist.type) {
            PlaylistType.M3U_URL -> openUrl(playlist.source).use { importM3u(playlist.id, it) }
            PlaylistType.M3U_FILE -> openFile(playlist.source).use { importM3u(playlist.id, it) }
            PlaylistType.XTREAM -> {
                importXtream(playlist)
                null
            }
        }
        playlistDao.markRefreshed(playlist.id, System.currentTimeMillis(), epgUrl ?: playlist.epgUrl)
    }

    private fun openUrl(url: String): InputStream {
        val request = Request.Builder().url(url).build()
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
                                tvgId = item.tvgId,
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
        val server: HttpUrl = XtreamUrls.normalizeServer(playlist.source)
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
            xtreamApi.account(XtreamUrls.playerApi(server, username, password))
        } catch (e: HttpException) {
            // Paneli za pogrešnu lozinku često vraćaju 401/403/404 umesto JSON odgovora.
            if (e.code() in setOf(401, 403, 404)) throw PlaylistException(Kind.AUTH_FAILED, e) else throw e
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

        val writer = ChannelWriter(playlist.id)
        val categories = xtreamApi.liveCategories(
            XtreamUrls.playerApi(server, username, password, XtreamUrls.ACTION_LIVE_CATEGORIES),
        )
        for (category in categories) {
            val key = category.id ?: continue
            writer.ensureCategory(key, category.name?.takeIf { it.isNotBlank() } ?: key)
        }

        val body = xtreamApi.liveStreams(
            XtreamUrls.playerApi(server, username, password, XtreamUrls.ACTION_LIVE_STREAMS),
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
                        tvgId = stream.epgChannelId?.takeIf { e -> e.isNotBlank() },
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
    }
}
