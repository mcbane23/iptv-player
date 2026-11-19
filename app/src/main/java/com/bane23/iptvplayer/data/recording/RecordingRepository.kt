package com.bane23.iptvplayer.data.recording

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.storage.PublicFolder
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bane23.iptvplayer.domain.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Snimanje koje je u toku. */
data class ActiveRecording(val id: Long, val playlistId: Long, val channelKey: String, val channelName: String) {
    /**
     * Da li se snima ovaj kanal. Poredi se stalni ključ, a ne ID: ID kanala se menja kad se lista
     * osveži, a snimanje za to vreme traje.
     */
    fun isOf(channel: Channel): Boolean = playlistId == channel.playlistId && channelKey == channel.stableKey

    /** Isti oblik kao [channelKeyOf], za brzo traženje među više kanala. */
    val key: String get() = "$playlistId|$channelKey"

    companion object {
        fun channelKeyOf(channel: Channel): String = "${channel.playlistId}|${channel.stableKey}"
    }
}

/** Zašto snimanje nije moglo da krene. */
enum class RecordingStartError { NO_SOURCE, NO_SPACE, NO_STORAGE }

/** Zauzeće prostora: koliko zauzimaju svi snimci i koliko je slobodno tamo gde idu novi. */
data class RecordingStorage(val usedBytes: Long, val freeBytes: Long) {
    /** Slobodnog prostora je malo, pa snimanje uskoro neće moći da krene ili će stati. */
    val low: Boolean get() = freeBytes < RecordingRepository.LOW_FREE_BYTES
}

/** Mesto na koje snimci mogu da se čuvaju. [removable] je SD kartica ili USB memorija. */
data class RecordingLocation(val path: String, val removable: Boolean, val freeBytes: Long, val selected: Boolean)

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
    private val settings: SettingsStore,
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
        moveOldToPublic()
    }

    /**
     * Završeni snimci iz foldera aplikacije u memoriji uređaja prelaze u Downloads/Glowbox/Snimci
     * (Banetov izbor, 19. 11. 2026). Snimci na SD kartici i USB memoriji se ne diraju. Što ne može da
     * se prenese (nema mesta, nema dozvole) ostaje gde jeste i dalje radi.
     */
    private suspend fun moveOldToPublic() {
        try {
            val target = PublicFolder.directory(context, PUBLIC_FOLDER) ?: return
            PublicFolder.clearUnfinished(target)
            val old = listOfNotNull(appRoots().firstOrNull(), File(context.filesDir, "recordings"))
            for (row in dao.byStatus(RecordingEntity.STATUS_DONE)) {
                val file = File(row.path)
                if (row.path.isEmpty() || file.parentFile !in old || !file.exists()) continue
                val moved = PublicFolder.transfer(file, target, file.nameWithoutExtension, file.extension.ifEmpty { "ts" }) ?: continue
                if (dao.getById(row.id) == null) {
                    // Obrisan dok se prenosio.
                    moved.delete()
                } else {
                    dao.setPath(row.id, moved.absolutePath)
                }
                if (file.exists()) file.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Prenos nije obavezan: snimci koji nisu preneti ostaju u folderu aplikacije.
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
        launchJob(ActiveRecording(id, row.playlistId, row.channelKey, row.channelName), file, source, stopAt)
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
            // Dva brza pritiska na „Snimaj” ne smeju da pokrenu dva snimanja istog kanala.
            startLock.withLock { startLocked(channelId, title, stopAtMs) }
        }

    private suspend fun startLocked(channelId: Long, title: String?, stopAtMs: Long?): RecordingStartError? {
        val channel = channelDao.getById(channelId) ?: return RecordingStartError.NO_SOURCE
        if (_active.value.any { it.playlistId == channel.playlistId && it.channelKey == channel.stableKey }) return null
        val source = channels.streamSource(channelId) ?: return RecordingStartError.NO_SOURCE
        val dir = directory() ?: return RecordingStartError.NO_STORAGE
        if (dir.usableSpace < MIN_FREE_BYTES) return RecordingStartError.NO_SPACE
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
        launchJob(ActiveRecording(id, channel.playlistId, channel.stableKey, channel.name), file, source, stopAtMs)
        return null
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

    fun stopChannel(channel: Channel) {
        _active.value.filter { it.isOf(channel) }.forEach { stop(it.id) }
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
        RecordingStorage(
            // Po zapisima, a ne po folderima: neki snimci mogu još da budu na starom mestu.
            usedBytes = dao.observeAll().first().sumOf { if (it.path.isEmpty()) 0L else File(it.path).length() },
            freeBytes = directory()?.usableSpace ?: 0,
        )
    }

    /** Mesta koja uređaj trenutno nudi: memorija uređaja, pa SD kartice i USB memorije koje su priključene. */
    suspend fun locations(): List<RecordingLocation> = withContext(Dispatchers.IO) {
        val current = directory()?.absolutePath
        roots().mapIndexed { index, dir ->
            RecordingLocation(dir.absolutePath, removable = index > 0, freeBytes = dir.usableSpace, selected = dir.absolutePath == current)
        }
    }

    /** Bira gde idu novi snimci. Postojeći snimci ostaju gde jesu. */
    suspend fun setLocation(path: String) {
        val roots = withContext(Dispatchers.IO) { roots() }
        // Prvo mesto je podrazumevano; pamti se kao prazno, da ostane ispravno i ako se put promeni.
        settings.setRecordingDir(if (path == roots.firstOrNull()?.absolutePath) "" else path)
    }

    /**
     * Mesta za snimke; prvo je memorija uređaja: Downloads/Glowbox/Snimci, da se snimci vide u
     * upravniku fajlova i ostanu posle deinstalacije, a folder aplikacije kad on nije dostupan
     * (stariji Android bez dozvole). Ostala su folderi aplikacije na SD kartici i USB memoriji.
     */
    private fun roots(): List<File> {
        val app = appRoots()
        val public = PublicFolder.directory(context, PUBLIC_FOLDER) ?: return app
        return listOf(public) + app.drop(1)
    }

    /** Folderi aplikacije na svim priključenim memorijama (za njih ne treba dozvola); prvi je memorija uređaja. */
    private fun appRoots(): List<File> {
        val external = context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES).filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
        val all = external.ifEmpty { listOf(File(context.filesDir, "recordings")) }
        return all.filter { it.isDirectory || it.mkdirs() }
    }

    /** Folder za nove snimke: izabrano mesto ako je još priključeno, inače memorija uređaja. */
    private suspend fun directory(): File? {
        val roots = roots()
        val chosen = settings.recordingDir.first()
        return roots.firstOrNull { it.absolutePath == chosen } ?: roots.firstOrNull()
    }

    companion object {
        /** Ispod ovoliko slobodnog prostora snimanje ne kreće, a ono koje traje se zaustavlja. */
        const val MIN_FREE_BYTES = 300L * 1024 * 1024

        /** Ispod ovoliko se na ekranu „Snimci” upozorava da ponestaje prostora. */
        const val LOW_FREE_BYTES = 2L * 1024 * 1024 * 1024
        private const val SAVE_EVERY_MS = 5_000L
        private const val PUBLIC_FOLDER = "Snimci"

        /** Naziv fajla: naziv kanala bez znakova koje sistem fajlova ne podnosi, i vreme početka. */
        fun fileName(channelName: String, startedAt: Long): String {
            val safe = channelName.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40).ifEmpty { "snimak" }
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAt))
            return "${safe}_$time.ts"
        }
    }
}
