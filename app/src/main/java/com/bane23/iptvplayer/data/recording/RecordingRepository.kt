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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Long, Job>()

    private val _active = MutableStateFlow<List<ActiveRecording>>(emptyList())

    /** Snimanja koja su u toku. */
    val active: StateFlow<List<ActiveRecording>> = _active.asStateFlow()

    val recordings: Flow<List<RecordingEntity>> = dao.observeAll()

    init {
        // Snimanja koja je prekinulo gašenje aplikacije ili uređaja ostaju kao završeni snimci.
        scope.launch {
            for (row in dao.byStatus(RecordingEntity.STATUS_RECORDING)) {
                if (synchronized(jobs) { row.id in jobs }) continue
                val size = File(row.path).length()
                if (size > 0) {
                    dao.finish(row.id, RecordingEntity.STATUS_DONE, RecordingEntity.ERROR_STREAM, File(row.path).lastModified(), size)
                } else {
                    File(row.path).delete()
                    dao.delete(row.id)
                }
            }
        }
    }

    /**
     * Počinje snimanje kanala. [title] je emisija koja trenutno ide (ako se zna), a [stopAtMs]
     * vreme kad snimanje samo staje. Vraća grešku kad snimanje ne može ni da krene.
     */
    suspend fun start(channelId: Long, title: String?, stopAtMs: Long? = null): RecordingStartError? =
        withContext(Dispatchers.IO) {
            if (_active.value.any { it.channelId == channelId }) return@withContext null
            val channel = channels.getChannel(channelId) ?: return@withContext RecordingStartError.NO_SOURCE
            val source = channels.streamSource(channelId) ?: return@withContext RecordingStartError.NO_SOURCE
            val dir = directory() ?: return@withContext RecordingStartError.NO_STORAGE
            if (dir.usableSpace < MIN_FREE_BYTES) return@withContext RecordingStartError.NO_SPACE
            val now = System.currentTimeMillis()
            val file = File(dir, fileName(channel.name, now))
            val id = dao.insert(
                RecordingEntity(
                    channelName = channel.name,
                    title = title?.takeIf { it.isNotBlank() } ?: channel.name,
                    path = file.absolutePath,
                    startedAt = now,
                    stopAtMs = stopAtMs,
                    status = RecordingEntity.STATUS_RECORDING,
                ),
            )
            val entry = ActiveRecording(id, channelId, channel.name)
            synchronized(jobs) {
                _active.update { it + entry }
                jobs[id] = scope.launch { run(id, file, source, stopAtMs) }
            }
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
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
        File(row.path).delete()
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
