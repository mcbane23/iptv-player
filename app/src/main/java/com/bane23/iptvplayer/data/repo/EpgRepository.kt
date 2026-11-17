package com.bane23.iptvplayer.data.repo

import androidx.room.withTransaction
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.EpgProgramEntity
import com.bane23.iptvplayer.data.db.PlaylistEntity
import com.bane23.iptvplayer.work.EpgScheduler
import com.bane23.iptvplayer.data.epg.XmltvItem
import com.bane23.iptvplayer.data.epg.XmltvParser
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.domain.Program
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Stanje vodiča jedne liste, za prikaz na ekranu vodiča. */
data class EpgStatus(
    val loading: Boolean = false,
    /** Kratak tehnički opis poslednje greške, ili null ako je poslednje učitavanje uspelo. */
    val error: String? = null,
    /** Lista nema nijedan izvor vodiča. */
    val noSource: Boolean = false,
    val hasPrograms: Boolean = false,
    val refreshedAt: Long? = null,
)

/** TV vodič: učitavanje XMLTV-a u bazu i čitanje emisija. */
@Singleton
class EpgRepository @Inject constructor(
    private val db: AppDatabase,
    private val httpClient: OkHttpClient,
    private val cipher: PasswordCipher,
    private val scheduler: EpgScheduler,
) {
    private val playlistDao = db.playlistDao()
    private val channelDao = db.channelDao()
    private val epgDao = db.epgDao()

    private data class Progress(val loading: Boolean = false, val error: String? = null, val noSource: Boolean = false)

    private val progress = MutableStateFlow<Map<Long, Progress>>(emptyMap())

    /** Dva učitavanja ne idu u isto vreme: vodiči su veliki, a upis u bazu je ionako jedan po jedan. */
    private val refreshLock = Mutex()

    fun observeStatus(playlistId: Long): Flow<EpgStatus> = combine(
        progress,
        epgDao.observeHasPrograms(playlistId),
        playlistDao.observeById(playlistId),
    ) { all, hasPrograms, playlist ->
        val current = all[playlistId] ?: Progress()
        EpgStatus(
            loading = current.loading,
            error = current.error,
            noSource = current.noSource,
            hasPrograms = hasPrograms,
            refreshedAt = playlist?.epgRefreshedAt,
        )
    }

    /** Zakazuje učitavanje vodiča u pozadini; rezultat se vidi kroz [observeStatus]. */
    fun requestRefresh(playlistId: Long) {
        setProgress(playlistId, Progress(loading = true))
        scheduler.refreshNow(playlistId)
    }

    /** Osvežava vodiče koji su stariji od nekoliko sati i briše emisije koje su odavno završene. */
    suspend fun refreshStale() {
        val now = System.currentTimeMillis()
        prune(now)
        for (playlist in playlistDao.getAll()) {
            val refreshedAt = playlist.epgRefreshedAt
            if (refreshedAt == null || now - refreshedAt > STALE_AFTER_MS) refresh(playlist.id)
        }
    }

    /** Preuzima vodič liste i upisuje emisije za njene kanale. Vraća true ako je uspelo. */
    suspend fun refresh(playlistId: Long): Boolean = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val playlist = playlistDao.getById(playlistId) ?: return@withLock false
            val url = sourceUrl(playlist)
            if (url == null) {
                setProgress(playlistId, Progress(noSource = true))
                return@withLock false
            }
            setProgress(playlistId, Progress(loading = true))
            val generation = System.currentTimeMillis()
            try {
                import(playlist, url, generation)
                setProgress(playlistId, Progress())
                true
            } catch (e: CancellationException) {
                epgDao.deleteGeneration(playlistId, generation)
                setProgress(playlistId, Progress())
                throw e
            } catch (e: Exception) {
                epgDao.deleteGeneration(playlistId, generation)
                // Poruka izuzetka može da sadrži adresu sa lozinkom, zato se prikazuje samo vrsta greške.
                val detail = if (e is HttpStatusException) "HTTP ${e.code}" else e.javaClass.simpleName
                setProgress(playlistId, Progress(error = detail))
                false
            }
        }
    }

    /** Šta je na kanalu sada i šta sledi. */
    suspend fun nowNext(channelId: Long): NowNext {
        val channel = channelDao.getById(channelId) ?: return NowNext()
        val key = channel.epgKey ?: return NowNext()
        val now = System.currentTimeMillis()
        val upcoming = epgDao.upcoming(channel.playlistId, key, now, limit = 2).map { it.toDomain() }
        val current = upcoming.firstOrNull()?.takeIf { it.startMs <= now }
        val next = if (current != null) upcoming.getOrNull(1) else upcoming.firstOrNull()
        return NowNext(current, next)
    }

    /** Emisije kanala koje tek počinju, najviše [limit], po redu. */
    suspend fun later(channelId: Long, limit: Int): List<Program> {
        val channel = channelDao.getById(channelId) ?: return emptyList()
        val key = channel.epgKey ?: return emptyList()
        val now = System.currentTimeMillis()
        return epgDao.upcoming(channel.playlistId, key, now, limit = limit + 1)
            .map { it.toDomain() }
            .filter { it.startMs > now }
            .take(limit)
    }

    /** Emisije kanala koje se bar delom prikazuju u periodu od [from] do [to]. */
    suspend fun programs(channelId: Long, from: Long, to: Long): List<Program> {
        val channel = channelDao.getById(channelId) ?: return emptyList()
        val key = channel.epgKey ?: return emptyList()
        return epgDao.range(channel.playlistId, key, from, to).map { it.toDomain() }
    }

    /** Izvor vodiča: link koji je uneo korisnik, pa link iz M3U zaglavlja, pa Xtream nalog. */
    private fun sourceUrl(playlist: PlaylistEntity): String? {
        playlist.epgUrlOverride?.takeIf { it.isNotBlank() }?.let { return it }
        playlist.epgUrl?.takeIf { it.isNotBlank() }?.let { return it }
        if (playlist.type != PlaylistType.XTREAM) return null
        val server = XtreamUrls.normalizeServer(playlist.source) ?: return null
        val username = playlist.username ?: return null
        val password = try {
            cipher.decrypt(playlist.passwordEncrypted ?: return null)
        } catch (_: Exception) {
            return null
        }
        return XtreamUrls.xmltv(server, username, password)
    }

    private suspend fun import(playlist: PlaylistEntity, url: String, generation: Long) {
        val channels = channelDao.epgRows(playlist.id)
        // Kanali sa tvg-id se vezuju preko njega; ostali preko naziva kanala iz vodiča.
        val wanted = HashSet<String>()
        val idsByName = HashMap<String, MutableList<Long>>()
        for (channel in channels) {
            val tvgId = channel.tvgId?.trim()?.lowercase()
            if (!tvgId.isNullOrEmpty()) {
                wanted.add(tvgId)
            } else {
                idsByName.getOrPut(channel.name.trim().lowercase()) { ArrayList(1) }.add(channel.id)
            }
        }
        val matchedByName = HashMap<String, MutableList<Long>>()
        // Za kanale sa arhivom iz M3U liste vodič se čuva danima unazad (vidi ArchiveRepository).
        val archiveIds = HashSet<Long>()
        val archiveKeys = HashSet<String>()
        for (channel in channels) {
            val type = channel.catchupType
            if (type == null || type == ArchiveRepository.TYPE_XTREAM) continue
            archiveIds.add(channel.id)
            channel.tvgId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(archiveKeys::add)
        }

        val shiftMs = playlist.epgShiftHours * HOUR_MS
        val now = System.currentTimeMillis()
        val from = now - KEEP_PAST_MS
        val archiveFrom = now - KEEP_ARCHIVE_MS
        val to = now + KEEP_FUTURE_MS
        val batch = ArrayList<EpgProgramEntity>(BATCH_SIZE)

        openGuide(url, playlist.userAgent).use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            for (item in XmltvParser.parse(reader)) {
                when (item) {
                    is XmltvItem.Channel -> {
                        val key = item.id.lowercase()
                        if (key in wanted) continue
                        for (name in item.displayNames) {
                            val ids = idsByName[name.lowercase()] ?: continue
                            matchedByName.getOrPut(key) { ArrayList() }.addAll(ids)
                            if (ids.any { it in archiveIds }) archiveKeys.add(key)
                        }
                    }

                    is XmltvItem.Program -> {
                        val key = item.channelId.lowercase()
                        if (key !in wanted && key !in matchedByName) continue
                        val start = item.startMs + shiftMs
                        val stop = item.stopMs + shiftMs
                        if (stop <= (if (key in archiveKeys) archiveFrom else from) || start >= to) continue
                        batch.add(
                            EpgProgramEntity(
                                playlistId = playlist.id,
                                channelKey = key,
                                startMs = start,
                                stopMs = stop,
                                title = item.title,
                                description = item.description?.take(MAX_DESCRIPTION),
                                generation = generation,
                            ),
                        )
                        if (batch.size >= BATCH_SIZE) {
                            epgDao.insertAll(ArrayList(batch))
                            batch.clear()
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) epgDao.insertAll(batch)

        db.withTransaction {
            for ((key, ids) in matchedByName) {
                ids.distinct().chunked(ID_CHUNK).forEach { channelDao.setEpgKey(it, key) }
            }
            for (key in archiveKeys) {
                val before = epgDao.firstStart(playlist.id, key, generation) ?: Long.MAX_VALUE
                epgDao.adoptEarlier(playlist.id, key, generation, before)
            }
            epgDao.deleteOtherGenerations(playlist.id, generation)
            playlistDao.markEpgRefreshed(playlist.id, System.currentTimeMillis())
        }
    }

    private suspend fun prune(now: Long) = epgDao.deleteEndedBefore(now - KEEP_PAST_MS, now - KEEP_ARCHIVE_MS)

    /** Otvara vodič; ako https veza ne može da se uspostavi proba http, a `.gz` raspakuje u hodu. */
    private fun openGuide(url: String, userAgent: String?): InputStream {
        val raw = try {
            openUrl(url, userAgent)
        } catch (e: IOException) {
            val parsed = url.toHttpUrlOrNull()
            val connectionLevel = e is ConnectException || e is SSLException
            if (parsed == null || !parsed.isHttps || !connectionLevel) throw e
            openUrl(parsed.newBuilder().scheme("http").build().toString(), userAgent)
        }
        val buffered = BufferedInputStream(raw, IO_BUFFER)
        return try {
            buffered.mark(2)
            val first = buffered.read()
            val second = buffered.read()
            buffered.reset()
            val gzipped = first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2
            if (gzipped) GZIPInputStream(buffered, IO_BUFFER) else buffered
        } catch (e: IOException) {
            buffered.close()
            throw e
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
            throw HttpStatusException(code)
        }
        return response.body.byteStream()
    }

    private fun setProgress(playlistId: Long, value: Progress) {
        progress.update { it + (playlistId to value) }
    }

    private fun EpgProgramEntity.toDomain() = Program(startMs, stopMs, title, description)

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val KEEP_PAST_MS = 6 * HOUR_MS
        const val KEEP_ARCHIVE_MS = ArchiveRepository.MAX_DAYS * ArchiveRepository.DAY_MS
        const val KEEP_FUTURE_MS = 48 * HOUR_MS
        const val STALE_AFTER_MS = 6 * HOUR_MS
        const val BATCH_SIZE = 1_000
        const val ID_CHUNK = 500
        const val MAX_DESCRIPTION = 1_000
        const val IO_BUFFER = 64 * 1024
        const val GZIP_MAGIC_1 = 0x1f
        const val GZIP_MAGIC_2 = 0x8b
    }
}
