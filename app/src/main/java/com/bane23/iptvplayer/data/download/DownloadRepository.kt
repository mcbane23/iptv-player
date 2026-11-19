package com.bane23.iptvplayer.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.DownloadEntity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.domain.StreamSource
import com.bane23.iptvplayer.player.PlaybackActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preuzimanje koje upravo traje. [totalBytes] je 0 dok server ne javi veličinu fajla. */
data class ActiveDownload(val id: Long, val title: String, val bytes: Long, val totalBytes: Long)

/** Zašto preuzimanje nije moglo da se doda u red. */
enum class DownloadStartError { NO_PLAYLIST, NO_STORAGE, NO_SPACE }

/** Koliko zauzimaju preuzeti fajlovi i koliko je slobodno u memoriji uređaja. */
data class DownloadStorage(val usedBytes: Long, val freeBytes: Long)

/**
 * Filmovi i epizode preuzeti na telefon. Preuzimanja idu jedno po jedno, redom kojim su dodata.
 * Staju dok se nešto gleda (nalog često dozvoljava samo jednu vezu) i dok nema Wi-Fi mreže, osim
 * ako su u podešavanjima dozvoljeni mobilni podaci; posle toga se sama nastavljaju. Dok preuzimanje
 * traje, [DownloadService] drži aplikaciju živom i u pozadini.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val vod: VodRepository,
    private val downloader: FileDownloader,
    private val settings: SettingsStore,
    network: NetworkWatcher,
) {
    private val dao = db.downloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Budi red kad se nešto doda ili nastavi. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var currentId: Long? = null
    private var currentJob: Job? = null

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    private val _active = MutableStateFlow<ActiveDownload?>(null)
    val active: StateFlow<ActiveDownload?> = _active.asStateFlow()

    /** Zašto red trenutno stoji; null kad preuzimanje sme da ide. */
    val hold: StateFlow<DownloadHold?> =
        combine(PlaybackActivity.playing, network.kind, settings.downloadOnMobile) { playing, kind, mobile ->
            DownloadRules.hold(playing, kind, mobile)
        }.stateIn(scope, SharingStarted.Eagerly, DownloadHold.OFFLINE)

    init {
        scope.launch { pump() }
    }

    /** Vodi red: dok ništa ne zadržava, preuzima jedno po jedno. Kad zadržavanje počne, prekida ono što traje. */
    private suspend fun pump() {
        hold.collectLatest { reason ->
            if (reason != null) return@collectLatest
            // Kad se plejer zatvori, server još koji trenutak računa njegovu vezu.
            delay(RESUME_DELAY_MS)
            while (true) {
                val next = dao.nextQueued()
                if (next == null) {
                    wake.receive()
                    continue
                }
                coroutineScope {
                    val job = launch { run(next) }
                    synchronized(lock) {
                        currentId = next.id
                        currentJob = job
                    }
                    try {
                        job.join()
                    } finally {
                        synchronized(lock) {
                            currentId = null
                            currentJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun run(row: DownloadEntity) {
        val file = File(row.path)
        var total = row.totalBytes
        var done = false
        _active.value = ActiveDownload(row.id, row.label, file.length(), total)
        startService()
        try {
            var failures = 0
            while (true) {
                val before = file.length()
                if (freeSpace(file) < MIN_FREE_BYTES) return fail(row.id, DownloadEntity.ERROR_SPACE)
                val source = try {
                    vod.downloadSource(row.playlistId, row.kind == DownloadEntity.KIND_EPISODE, row.itemId, row.extension)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Liste više nema ili njeni podaci ne mogu da se pročitaju.
                    return fail(row.id, DownloadEntity.ERROR_SOURCE)
                }
                try {
                    var lastShown = 0L
                    var lastSaved = SystemClock.elapsedRealtime()
                    var noSpace = false
                    val finished = downloader.download(source, file) { bytes, totalBytes ->
                        total = totalBytes
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastShown >= SHOW_EVERY_MS) {
                            lastShown = now
                            _active.value = ActiveDownload(row.id, row.label, bytes, totalBytes)
                        }
                        if (now - lastSaved >= SAVE_EVERY_MS) {
                            lastSaved = now
                            dao.setProgress(row.id, bytes, totalBytes)
                            noSpace = freeSpace(file) < MIN_FREE_BYTES
                        }
                        !noSpace
                    }
                    if (noSpace) return fail(row.id, DownloadEntity.ERROR_SPACE)
                    if (finished) {
                        val size = file.length()
                        dao.setProgress(row.id, size, size)
                        dao.setStatus(row.id, DownloadEntity.STATUS_DONE, null)
                        done = true
                        return
                    }
                } catch (_: DownloadRefusedException) {
                    return fail(row.id, DownloadEntity.ERROR_SOURCE)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    // Veza je pukla. Ako je pre toga nešto stiglo, server radi, pa se brojanje vraća na nulu.
                    if (file.length() > before) failures = 0
                    if (++failures > MAX_FAILURES) return fail(row.id, DownloadEntity.ERROR_SOURCE)
                    delay(RETRY_DELAY_MS)
                }
            }
        } finally {
            withContext(NonCancellable) {
                // Zapis možda više ne postoji (obrisano preuzimanje); tada se ništa ne menja.
                if (!done) dao.setProgress(row.id, file.length(), total)
                _active.value = null
            }
        }
    }

    private suspend fun fail(id: Long, error: String) = dao.setStatus(id, DownloadEntity.STATUS_FAILED, error)

    private fun freeSpace(file: File): Long = file.parentFile?.usableSpace ?: Long.MAX_VALUE

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (_: IllegalStateException) {
            // Sistem nije dozvolio servis iz pozadine: preuzima se dok aplikacija ostane živa.
        } catch (_: SecurityException) {
            // Isto.
        }
    }

    /** Dodaje film u red za preuzimanje. Film koji je već u spisku se nastavlja ako je stajao. */
    suspend fun enqueueMovie(movieId: Long, title: String, poster: String?, extension: String): DownloadStartError? =
        enqueue(DownloadEntity.KIND_MOVIE, movieId, seriesId = null, title, subtitle = null, poster, extension)

    /** Dodaje epizodu u red za preuzimanje; [seriesTitle] i [poster] su od serije. */
    suspend fun enqueueEpisode(seriesId: Long, seriesTitle: String, poster: String?, episode: Episode): DownloadStartError? =
        enqueue(
            DownloadEntity.KIND_EPISODE,
            episode.id,
            seriesId,
            seriesTitle,
            DownloadRules.episodeLabel(episode.season, episode.number),
            poster,
            episode.extension,
        )

    private suspend fun enqueue(
        kind: String,
        itemId: Long,
        seriesId: Long?,
        title: String,
        subtitle: String?,
        poster: String?,
        extension: String,
    ): DownloadStartError? = withContext(Dispatchers.IO) {
        val playlistId = settings.activePlaylistId.first() ?: return@withContext DownloadStartError.NO_PLAYLIST
        val existing = dao.find(playlistId, kind, itemId)
        if (existing != null) {
            if (existing.status == DownloadEntity.STATUS_PAUSED || existing.status == DownloadEntity.STATUS_FAILED) resume(existing.id)
            return@withContext null
        }
        val dir = directory() ?: return@withContext DownloadStartError.NO_STORAGE
        if (dir.usableSpace < MIN_FREE_BYTES) return@withContext DownloadStartError.NO_SPACE
        val file = File(dir, DownloadRules.fileName(kind, playlistId, itemId, extension))
        // Ostatak nekog ranijeg, obrisanog preuzimanja ne sme da se nadoveže na novi fajl.
        file.delete()
        dao.insert(
            DownloadEntity(
                playlistId = playlistId,
                kind = kind,
                itemId = itemId,
                seriesId = seriesId,
                title = title,
                subtitle = subtitle,
                poster = poster,
                extension = extension,
                path = file.absolutePath,
                status = DownloadEntity.STATUS_QUEUED,
                createdAt = System.currentTimeMillis(),
            ),
        )
        wake.trySend(Unit)
        null
    }

    /** Pauzira preuzimanje dok ga korisnik sam ne nastavi; ono što je preuzeto ostaje. */
    suspend fun pause(id: Long) {
        val row = dao.getById(id) ?: return
        if (row.status != DownloadEntity.STATUS_QUEUED) return
        dao.setStatus(id, DownloadEntity.STATUS_PAUSED, null)
        cancelIfCurrent(id)
    }

    /** Pauzira ono što se upravo preuzima (dugme u obaveštenju). */
    suspend fun pauseActive() {
        _active.value?.let { pause(it.id) }
    }

    /** Vraća pauzirano ili neuspelo preuzimanje u red; nastavlja tamo gde je stalo. */
    suspend fun resume(id: Long) {
        val row = dao.getById(id) ?: return
        if (row.status != DownloadEntity.STATUS_PAUSED && row.status != DownloadEntity.STATUS_FAILED) return
        dao.setStatus(id, DownloadEntity.STATUS_QUEUED, null)
        wake.trySend(Unit)
    }

    /** Briše preuzimanje i njegov fajl; ono koje traje se prvo prekida. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext
        // Prvo se sklanja iz reda, da ga red ne pokrene ponovo dok se briše.
        dao.setStatus(id, DownloadEntity.STATUS_PAUSED, null)
        cancelIfCurrent(id)
        File(row.path).delete()
        dao.delete(id)
    }

    private suspend fun cancelIfCurrent(id: Long) {
        val job = synchronized(lock) { currentJob.takeIf { currentId == id } } ?: return
        job.cancel()
        job.join()
    }

    suspend fun get(id: Long): DownloadEntity? = dao.getById(id)

    /**
     * Fajl na uređaju za film ili epizodu aktivne liste, ako je preuzet do kraja; inače null, pa se
     * pušta sa interneta.
     */
    suspend fun localSource(episode: Boolean, itemId: Long): StreamSource? = withContext(Dispatchers.IO) {
        val playlistId = settings.activePlaylistId.first() ?: return@withContext null
        val kind = if (episode) DownloadEntity.KIND_EPISODE else DownloadEntity.KIND_MOVIE
        dao.find(playlistId, kind, itemId)?.let(::fileSource)
    }

    /** Izvor za puštanje preuzetog fajla, ili null kad nije ceo ili ga više nema. */
    fun fileSource(row: DownloadEntity): StreamSource? {
        if (row.status != DownloadEntity.STATUS_DONE) return null
        val file = File(row.path)
        if (!file.exists() || file.length() == 0L) return null
        return StreamSource(listOf(Uri.fromFile(file).toString()), userAgent = null, referrer = null)
    }

    suspend fun storage(): DownloadStorage = withContext(Dispatchers.IO) {
        val dir = directory()
        DownloadStorage(
            usedBytes = dir?.listFiles()?.sumOf { it.length() } ?: 0,
            freeBytes = dir?.usableSpace ?: 0,
        )
    }

    /** Folder aplikacije u memoriji uređaja (za njega ne treba dozvola); briše se kad se aplikacija deinstalira. */
    private fun directory(): File? {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.takeIf { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
        val dir = File(external ?: context.filesDir, FOLDER)
        return dir.takeIf { it.isDirectory || it.mkdirs() }
    }

    private val DownloadEntity.label: String get() = listOfNotNull(title, subtitle).joinToString(" · ")

    companion object {
        /** Ispod ovoliko slobodnog prostora preuzimanje ne kreće, a ono koje traje staje. */
        const val MIN_FREE_BYTES = 300L * 1024 * 1024
        private const val FOLDER = "preuzeto"
        private const val RESUME_DELAY_MS = 3_000L
        private const val SHOW_EVERY_MS = 1_000L
        private const val SAVE_EVERY_MS = 5_000L
        private const val RETRY_DELAY_MS = 5_000L
        private const val MAX_FAILURES = 5
    }
}
