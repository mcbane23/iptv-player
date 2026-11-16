package com.bane23.iptvplayer.data.recording

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.data.repo.ChannelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Snimanje koje je u toku. */
data class ActiveRecording(val id: Long, val channelId: Long, val channelName: String)

/** Zašto snimanje nije moglo da krene. */
enum class RecordingStartError { NO_SOURCE, NO_SPACE, NO_STORAGE }

/** Zauzeće prostora na mestu gde se čuvaju snimci. */
data class RecordingStorage(val usedBytes: Long, val freeBytes: Long)

/**
 * Snimci kanala: pokretanje i zaustavljanje snimanja, spisak i brisanje. Dok se nešto snima,
 * [RecordingService] drži aplikaciju živom i u pozadini.
 */
@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val channels: ChannelRepository,
    private val recorder: StreamRecorder,
) {
    private val dao = db.recordingDao()
    private val channelDao = db.channelDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Long, Job>()
    private val startLock = Mutex()

    private val _active = MutableStateFlow<List<ActiveRecording>>(emptyList())

    /** Snimanja koja su u toku. */
    val active: StateFlow<List<ActiveRecording>> = _active.asStateFlow()

    val recordings: Flow<List<RecordingEntity>> = dao.observeAll()

    init {
        scope.launch { restore() }
    }

    /**
     * Posle pokretanja aplikacije (i posle paljenja uređaja, kad sistem pokrene aplikaciju): snimanja koja je prekinulo gašenje ostaju kao završeni
     * snimci, a zakazana ponovo dobijaju budilnik (ili kreću odmah ako im je vreme već počelo).
     */
    private suspend fun restore() = withContext(Dispatchers.IO) {
        for (row in dao.byStatus(RecordingEntity.STATUS_RECORDING)) {
            if (synchronized(jobs) { row.id in jobs }) continue
            val file = File(row.path)
            val size = file.length()
            if (size > 0) {
                dao.finish(row.id, RecordingEntity.STATUS_DONE, RecordingEntity.ERROR_STREAM, file.lastModified(), size)
            } else {
                file.delete()
                dao.delete(row.id)
            }
        }
        val now = System.currentTimeMillis()
        for (row in dao.byStatus(RecordingEntity.STATUS_SCHEDULED)) {
            if (row.startedAt <= now) startScheduled(row.id) else RecordingAlarms.set(context, row.id, row.startedAt)
        }
    }

    /**
     * Zakazuje snimanje emisije od [startMs] do [stopMs]. Ako je emisija već počela, snimanje kreće odmah.
     */
    suspend fun schedule(channelId: Long, title: String, startMs: Long, stopMs: Long): RecordingStartError? =
        withContext(Dispatchers.IO) {
            if (startMs <= System.currentTimeMillis()) return@withContext start(channelId, title, stopMs)
            val channel = channelDao.getById(channelId) ?: return@withContext RecordingStartError.NO_SOURCE
            val id = dao.insert(
                RecordingEntity(
                    playlistId = channel.playlistId,
                    channelKey = channel.stableKey,
                    channelName = channel.name,
                    title = title.ifBlank { channel.name },
                    path = "",
                    startedAt = startMs,
                    stopAtMs = stopMs,
                    status = RecordingEntity.STATUS_SCHEDULED,
                ),
            )
            RecordingAlarms.set(context, id, startMs)
            null
        }

    /** Pokreće zakazano snimanje (budilnik, ili otvaranje aplikacije posle zakazanog vremena). */
    suspend fun startScheduled(id: Long) = withContext(Dispatchers.IO) {
        // Budilnik i otvaranje aplikacije mogu da stignu u isto vreme; snimanje sme da krene samo jednom.
        startLock.withLock { startScheduledLocked(id) }
    }

    private suspend fun startScheduledLocked(id: Long) {
        val row = dao.getById(id) ?: return
        if (row.status != RecordingEntity.STATUS_SCHEDULED) return
        val now = System.currentTimeMillis()
        suspend fun fail(error: String) = dao.finish(id, RecordingEntity.STATUS_FAILED, error, now, 0)
        val stopAt = row.stopAtMs
        if (stopAt != null && now >= stopAt) return fail(RecordingEntity.ERROR_MISSED)
        val channel = channelDao.byStableKey(row.playlistId, row.channelKey)
            ?: return fail(RecordingEntity.ERROR_NO_CHANNEL)
        val source = channels.streamSource(channel.id) ?: return fail(RecordingEntity.ERROR_NO_CHANNEL)
        val dir = directory()?.takeIf { it.usableSpace >= MIN_FREE_BYTES } ?: return fail(RecordingEntity.ERROR_SPACE)
        val file = File(dir, fileName(row.channelName, now))
        dao.markRecording(id, file.absolutePath, now)
        launchJob(ActiveRecording(id, channel.id, row.channelName), file, source, stopAt)
    }

    private fun launchJob(entry: ActiveRecording, file: File, source: com.bane23.iptvplayer.domain.StreamSource, stopAtMs: Long?) {
        synchronized(jobs) {
            _active.update { it + entry }
            jobs[entry.id] = scope.launch { run(entry.id, file, source, stopAtMs) }
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        } catch (_: IllegalStateException) {
            // Sistem nije dozvolio servis iz pozadine: snima se dok aplikacija ostane živa.
        }
    }

    /**
     * Počinje snimanje kanala. [title] je emisija koja trenutno ide (ako se zna), a [stopAtMs]
     * vreme kad snimanje samo staje. Vraća grešku kad snimanje ne može ni da krene.
     */
    suspend fun start(channelId: Long, title: String?, stopAtMs: Long? = null): RecordingStartError? =
        withContext(Dispatchers.IO) {
            if (_active.value.any { it.channelId == channelId }) return@withContext null
            val channel = channelDao.getById(channelId) ?: return@withContext RecordingStartError.NO_SOURCE
            val source = channels.streamSource(channelId) ?: return@withContext RecordingStartError.NO_SOURCE
            val dir = directory() ?: return@withContext RecordingStartError.NO_STORAGE
            if (dir.usableSpace < MIN_FREE_BYTES) return@withContext RecordingStartError.NO_SPACE
            val now = System.currentTimeMillis()
            val file = File(dir, fileName(channel.name, now))
            val id = dao.insert(
                RecordingEntity(
                    playlistId = channel.playlistId,
                    channelKey = channel.stableKey,
                    channelName = channel.name,
                    title = title?.takeIf { it.isNotBlank() } ?: channel.name,
                    path = file.absolutePath,
                    startedAt = now,
                    stopAtMs = stopAtMs,
                    status = RecordingEntity.STATUS_RECORDING,
                ),
            )
            launchJob(ActiveRecording(id, channelId, channel.name), file, source, stopAtMs)
            null
        }

    private suspend fun run(id: Long, file: File, source: com.bane23.iptvplayer.domain.StreamSource, stopAtMs: Long?) {
        var error: String? = null
        var lastSaved = 0L
        try {
            recorder.record(source, file) { size ->
                val now = System.currentTimeMillis()
                if (now - lastSaved >= SAVE_EVERY_MS) {
                    lastSaved = now
                    scope.launch { dao.setSize(id, size) }
                    if ((file.parentFile?.usableSpace ?: Long.MAX_VALUE) < MIN_FREE_BYTES) {
                        error = RecordingEntity.ERROR_SPACE
                        return@record false
                    }
                }
                stopAtMs == null || now < stopAtMs
            }
        } catch (_: CancellationException) {
            // Korisnik je zaustavio snimanje.
        } catch (_: UnsupportedStreamException) {
            error = RecordingEntity.ERROR_UNSUPPORTED
        } catch (_: IOException) {
            error = RecordingEntity.ERROR_STREAM
        } finally {
            withContext(NonCancellable) {
                val size = file.length()
                if (size > 0) {
                    dao.finish(id, RecordingEntity.STATUS_DONE, error, System.currentTimeMillis(), size)
                } else {
                    // Ništa nije snimljeno: ostaje samo zapis o neuspehu, bez praznog fajla.
                    file.delete()
                    dao.finish(id, RecordingEntity.STATUS_FAILED, error ?: RecordingEntity.ERROR_STREAM, System.currentTimeMillis(), 0)
                }
                synchronized(jobs) {
                    jobs.remove(id)
                    _active.update { list -> list.filterNot { it.id == id } }
                }
            }
        }
    }

    fun stop(id: Long) {
        synchronized(jobs) { jobs[id] }?.cancel()
    }

    fun stopChannel(channelId: Long) {
        _active.value.filter { it.channelId == channelId }.forEach { stop(it.id) }
    }

    fun stopAll() {
        synchronized(jobs) { jobs.values.toList() }.forEach { it.cancel() }
    }

    suspend fun get(id: Long): RecordingEntity? = dao.getById(id)

    /** Briše snimak i njegov fajl; snimanje koje je u toku se prvo zaustavlja. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val job = synchronized(jobs) { jobs[id] }
        job?.cancel()
        job?.join()
        val row = dao.getById(id) ?: return@withContext
        RecordingAlarms.cancel(context, id)
        if (row.path.isNotEmpty()) File(row.path).delete()
        dao.delete(id)
    }

    suspend fun storage(): RecordingStorage = withContext(Dispatchers.IO) {
        val dir = directory()
        RecordingStorage(
            usedBytes = dir?.listFiles()?.sumOf { it.length() } ?: 0,
            freeBytes = dir?.usableSpace ?: 0,
        )
    }

    /** Folder sa snimcima: deljena memorija uređaja (folder aplikacije), a bez nje interna. */
    private fun directory(): File? {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.takeIf { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
        val dir = external ?: File(context.filesDir, "recordings")
        return dir.takeIf { it.isDirectory || it.mkdirs() }
    }

    companion object {
        /** Ispod ovoliko slobodnog prostora snimanje ne kreće, a ono koje traje se zaustavlja. */
        const val MIN_FREE_BYTES = 300L * 1024 * 1024
        private const val SAVE_EVERY_MS = 5_000L

        /** Naziv fajla: naziv kanala bez znakova koje sistem fajlova ne podnosi, i vreme početka. */
        fun fileName(channelName: String, startedAt: Long): String {
            val safe = channelName.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40).ifEmpty { "snimak" }
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAt))
            return "${safe}_$time.ts"
        }
    }
}
