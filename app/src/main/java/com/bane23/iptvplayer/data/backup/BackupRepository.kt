package com.bane23.iptvplayer.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.bane23.iptvplayer.BuildConfig
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.lan.TransferTraktLogin
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.TransferRepository
import com.bane23.iptvplayer.data.storage.PublicFolder
import com.bane23.iptvplayer.data.trakt.TokenResponse
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.data.trakt.TraktTokenStore
import com.bane23.iptvplayer.ui.common.AppLanguage
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.ui.common.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Šta je uvoz uradio. */
data class BackupImportResult(
    /** Nove liste koje su dodate. */
    val added: Int,
    /** Liste koje je uređaj već imao. */
    val existing: Int,
    /** Liste koje nisu mogle da se učitaju. */
    val failed: Int,
    /** Jezik aplikacije je promenjen, pa ekran treba napraviti iznova. */
    val languageChanged: Boolean,
)

sealed interface BackupImportState {
    data object Idle : BackupImportState

    /** Uvoz traje; [total] je 0 dok se još ne učitavaju nove liste. */
    data class Running(val current: Int = 0, val total: Int = 0) : BackupImportState

    data class Done(val result: BackupImportResult) : BackupImportState

    /** Uvoz je prekinut greškom; ono što je do tada uvezeno ostaje. */
    data object Failed : BackupImportState
}

/** Fajl izvoza nađen u javnom folderu aplikacije. */
data class BackupFileInfo(val path: String, val name: String, val modifiedAt: Long)

/**
 * Izvoz svih lista, podešavanja i naloga u fajl i uvoz iz njega. Uvoz radi u pozadini i nastavlja se
 * i kad korisnik napusti ekran, jer učitavanje novih lista ume da traje minutima.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val transfers: TransferRepository,
    private val trakt: TraktRepository,
    private val traktTokens: TraktTokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _importState = MutableStateFlow<BackupImportState>(BackupImportState.Idle)
    val importState: StateFlow<BackupImportState> = _importState.asStateFlow()

    private val deviceKind: String
        get() = if (DeviceType.isTv(context)) Backup.DEVICE_TV else Backup.DEVICE_PHONE

    /** Predloženo ime fajla, sa današnjim datumom. */
    fun suggestedName(): String = "$FILE_PREFIX${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"

    /** Tekst fajla izvoza i broj lista koje nisu mogle da uđu u njega. Uz [password] je sadržaj šifrovan. */
    suspend fun exportText(password: String?): Pair<String, Int> = withContext(Dispatchers.IO) {
        val export = transfers.exportBundle(full = true)
        val (general, device) = settings.exportPrefs()
        val backup = Backup(
            createdAt = System.currentTimeMillis(),
            device = deviceKind,
            appVersion = BuildConfig.VERSION_NAME,
            // Podešavanja idu cela kroz `prefs`, pa se kraći spisak iz prenosa na TV ne ponavlja.
            bundle = export.bundle.copy(settings = null),
            prefs = general,
            devicePrefs = device,
            favoriteLists = db.favoriteListDao().all().map { it.name },
            trakt = traktLogin(),
            language = AppLocale.get(context).name,
        )
        BackupCodec.encode(backup, password) to export.skipped
    }

    private suspend fun traktLogin(): TransferTraktLogin? {
        val session = traktTokens.session() ?: return null
        val now = System.currentTimeMillis() / 1000
        return TransferTraktLogin(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresIn = (session.expiresAtMs / 1000 - now).coerceAtLeast(0),
            createdAt = now,
        )
    }

    /** Upisuje izvoz u fajl koji je korisnik izabrao sistemskim biračem. Vraća false kad upis ne uspe. */
    suspend fun writeTo(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
            output.use { it.write(text.toByteArray()) }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Upisuje izvoz u Downloads/Glowbox. Vraća fajl, ili null kad folder nije dostupan ili upis ne uspe. */
    suspend fun writeToPublicFolder(text: String): File? = withContext(Dispatchers.IO) {
        val dir = PublicFolder.directory(context) ?: return@withContext null
        val file = PublicFolder.freeFile(dir, suggestedName(), BackupCodec.EXTENSION)
        try {
            file.writeText(text)
            file
        } catch (_: IOException) {
            file.delete()
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Aplikacija vidi i fajlove u Downloads/Glowbox koje nije sama napravila (posle ponovne
     * instalacije, ili kad su preneti sa drugog uređaja). Pre Androida 11 za to je dovoljna dozvola
     * za memoriju, a od njega treba pristup svim fajlovima.
     */
    fun canReadForeignFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else PublicFolder.hasPermission(context)

    /** Fajlovi izvoza u Downloads/Glowbox, najnoviji prvi. */
    suspend fun publicFiles(): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        val dir = PublicFolder.directory(context) ?: return@withContext emptyList()
        try {
            dir.listFiles { file -> file.isFile && file.name.endsWith(".${BackupCodec.EXTENSION}", ignoreCase = true) }
                .orEmpty()
                .map { BackupFileInfo(it.absolutePath, it.name, it.lastModified()) }
                .sortedByDescending { it.modifiedAt }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** Tekst fajla koji je korisnik izabrao; null kad ne može da se pročita ili je prevelik. */
    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use(::readLimited)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Tekst fajla iz javnog foldera; null kad ne može da se pročita ili je prevelik. */
    suspend fun readText(path: String): String? = withContext(Dispatchers.IO) {
        try {
            File(path).inputStream().use(::readLimited)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun readLimited(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > BackupCodec.MAX_FILE_BYTES) return null
            out.write(buffer, 0, read)
        }
        return String(out.toByteArray())
    }

    /**
     * Pokreće uvoz u pozadini; tok se prati kroz [importState]. Uz [replace] aplikacija posle uvoza
     * sadrži samo ono što je u fajlu, inače se postojeće dopunjuje. Dok jedan uvoz traje, drugi se
     * ne pokreće.
     */
    fun startImport(backup: Backup, replace: Boolean) {
        if (_importState.value is BackupImportState.Running) return
        _importState.value = BackupImportState.Running()
        scope.launch {
            _importState.value = try {
                BackupImportState.Done(import(backup, replace))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupImportState.Failed
            }
        }
    }

    /** Ishod je prikazan; sledeći uvoz kreće od početka. */
    fun clearImportState() {
        if (_importState.value !is BackupImportState.Running) _importState.value = BackupImportState.Idle
    }

    /** Ekran je napravljen iznova zbog novog jezika; ishod uvoza ostaje da se vidi. */
    fun languageApplied() {
        val state = _importState.value
        if (state is BackupImportState.Done) _importState.value = BackupImportState.Done(state.result.copy(languageChanged = false))
    }

    private suspend fun import(backup: Backup, replace: Boolean): BackupImportResult {
        // Podešavanja vezana za uređaj važe samo kad izvoz potiče sa iste vrste uređaja.
        val sameDevice = backup.device == deviceKind
        settings.importPrefs(backup.prefs, backup.devicePrefs.takeIf { sameDevice }, replace)
        val languageChanged = applyLanguage(backup.language)
        val pending = transfers.beginImport(backup.bundle, replace = replace, favoriteListNames = backup.favoriteLists)
        backup.trakt?.let { adoptTrakt(it) }
        val result = pending.finish { current, total -> _importState.value = BackupImportState.Running(current, total) }
        return BackupImportResult(result.added, result.existing, result.failed, languageChanged)
    }

    private fun applyLanguage(name: String?): Boolean {
        val language = AppLanguage.entries.firstOrNull { it.name == name } ?: return false
        if (AppLocale.get(context) == language) return false
        AppLocale.set(context, language)
        return true
    }

    /** Prijava iz fajla se uzima samo kad uređaj nije već prijavljen na Trakt. */
    private suspend fun adoptTrakt(login: TransferTraktLogin) {
        if (login.accessToken.isBlank() || login.refreshToken.isBlank()) return
        if (login.accessToken.length > MAX_TOKEN || login.refreshToken.length > MAX_TOKEN) return
        try {
            trakt.adoptLogin(TokenResponse(login.accessToken, login.refreshToken, login.expiresIn, login.createdAt))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Bez Trakt prijave uvoz je i dalje uspeo; korisnik se prijavljuje ručno.
        }
    }

    private companion object {
        const val FILE_PREFIX = "glowbox-podesavanja-"
        const val MAX_TOKEN = 2_000
    }
}
