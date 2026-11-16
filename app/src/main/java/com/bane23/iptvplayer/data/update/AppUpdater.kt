package com.bane23.iptvplayer.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Stanje ažuriranja aplikacije, za dijalog i za red u podešavanjima. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** Provera je pokrenuta ručno i nema novije verzije. */
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    /** [progress] je od 0 do 1, ili null kad veličina nije poznata. */
    data class Downloading(val manifest: UpdateManifest, val progress: Float?) : UpdateState
    /** APK je preuzet, ali korisnik mora da dozvoli instaliranje iz ove aplikacije. */
    data class NeedsPermission(val manifest: UpdateManifest) : UpdateState
    /** Sistem prikazuje potvrdu instalacije (ili instalira sam). */
    data class Installing(val manifest: UpdateManifest) : UpdateState
    data class Failed(val reason: UpdateError) : UpdateState
}

enum class UpdateError { NETWORK, BAD_FILE, INSTALL }

/**
 * Proverava javni repo za noviji build, preuzima APK i predaje ga sistemskom instaleru.
 * Android uvek traži potvrdu „Instaliraj”, osim kad je ova aplikacija već instalirala samu sebe
 * (Android 12+), tada ažuriranje može da prođe i bez dodira.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private var checkedOnStart = false
    private val apkFile: File get() = File(File(context.cacheDir, "update"), "update.apk")

    val installedVersionName: String
        get() = runCatching { packageInfo().versionName }.getOrNull().orEmpty()

    private val installedVersionCode: Long
        get() = runCatching { PackageInfoCompat.getLongVersionCode(packageInfo()) }.getOrDefault(Long.MAX_VALUE)

    @Suppress("DEPRECATION")
    private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

    /** Tiha provera pri startu, jednom po pokretanju. Greške se ne prikazuju. */
    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        // Ostatak prethodnog ažuriranja više ne treba.
        scope.launch { apkFile.parentFile?.deleteRecursively() }
        check(userInitiated = false)
    }

    fun check(userInitiated: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (userInitiated) _state.value = UpdateState.Checking
            val manifest = runCatching { fetchManifest() }.getOrNull()
            _state.value = when {
                manifest == null -> if (userInitiated) UpdateState.Failed(UpdateError.NETWORK) else UpdateState.Idle
                UpdateManifestParser.isNewer(manifest, installedVersionCode) -> UpdateState.Available(manifest)
                userInitiated -> UpdateState.UpToDate
                else -> UpdateState.Idle
            }
        }
    }

    /** Korisnik je odbio ili zatvorio poruku. Preuzimanje u toku se prekida. */
    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun downloadAndInstall() {
        val manifest = when (val s = _state.value) {
            is UpdateState.Available -> s.manifest
            is UpdateState.NeedsPermission -> s.manifest
            else -> return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            val file = apkFile
            if (!file.exists() || !hashMatches(file, manifest)) {
                _state.value = UpdateState.Downloading(manifest, null)
                val ok = runCatching { download(manifest, file) }.getOrElse { false }
                if (!ok) {
                    file.delete()
                    _state.value = UpdateState.Failed(UpdateError.NETWORK)
                    return@launch
                }
                if (!hashMatches(file, manifest)) {
                    file.delete()
                    _state.value = UpdateState.Failed(UpdateError.BAD_FILE)
                    return@launch
                }
            }
            if (!canInstall()) {
                _state.value = UpdateState.NeedsPermission(manifest)
                return@launch
            }
            _state.value = UpdateState.Installing(manifest)
            runCatching { install(file) }.onFailure { onInstallFailed() }
        }
    }

    /** Da li je korisnik dozvolio instaliranje aplikacija iz ove aplikacije (Android 8+). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Sistemski instaler je odbio ili je korisnik otkazao. */
    fun onInstallFailed() {
        _state.value = UpdateState.Failed(UpdateError.INSTALL)
    }

    /** Korisnik je odustao na ekranu potvrde: nudi se ponovo, APK je već preuzet. */
    fun onInstallCancelled() {
        val s = _state.value
        if (s is UpdateState.Installing) _state.value = UpdateState.Available(s.manifest)
    }

    private fun fetchManifest(): UpdateManifest? {
        val request = Request.Builder()
            .url(UpdateSource.manifestUrl)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return UpdateManifestParser.parse(response.body.string())
        }
    }

    private suspend fun download(manifest: UpdateManifest, file: File): Boolean {
        file.parentFile?.mkdirs()
        val request = Request.Builder().url(UpdateSource.apkUrl(manifest)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body
            val total = body.contentLength()
            var done = 0L
            var lastStep = -1
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val step = (done * 100 / total).toInt()
                            if (step != lastStep) {
                                lastStep = step
                                _state.value = UpdateState.Downloading(manifest, step / 100f)
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    private fun hashMatches(file: File, manifest: UpdateManifest): Boolean {
        val expected = manifest.sha256 ?: return file.length() > 0
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Kad je ova aplikacija već instalirala samu sebe, sistem može da preskoči potvrdu.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("app.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            // Sistem dopisuje status u intent, zato mora biti promenljiv (intent je eksplicitan).
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
