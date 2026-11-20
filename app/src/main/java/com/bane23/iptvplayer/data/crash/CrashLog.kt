package com.bane23.iptvplayer.data.crash

import android.content.Context
import android.os.Build
import com.bane23.iptvplayer.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Poslednji zabeleženi pad. [fresh] znači da ga korisnik još nije video (pad je bio u prošlom pokretanju). */
data class CrashReport(val timeMs: Long, val text: String, val fresh: Boolean)

/**
 * Beleži poslednji pad aplikacije u fajl na samom uređaju. Ništa se nikuda ne šalje: izveštaj
 * napušta uređaj samo kad ga korisnik sam podeli ili sačuva iz podešavanja.
 *
 * Radi bez Hilt-a i bez korutina, jer mora da radi i kad pad nastane pre nego što se aplikacija
 * do kraja pokrene, a u trenutku pada sme samo da upiše fajl i preda dalje sistemu.
 */
object CrashLog {
    private const val DIR = "crash"
    private const val FILE = "last.txt"

    /** Postoji dok korisnik ne vidi izveštaj. */
    private const val FRESH_MARK = "new"

    /** Trag steka ume da bude ogroman (rekurzija); ovoliko je dovoljno da se vidi uzrok. */
    private const val MAX_CHARS = 40_000

    private val _report = MutableStateFlow<CrashReport?>(null)

    /** Poslednji zabeleženi pad, ili null kad ga nema. */
    val report: StateFlow<CrashReport?> = _report.asStateFlow()

    private var installed = false

    /** Poziva se jednom, na samom početku rada aplikacije. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val dir = File(context.filesDir, DIR)
        _report.value = read(dir)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(dir, format(System.currentTimeMillis(), thread.name, error, deviceInfo()))
            } catch (_: Throwable) {
                // Beleženje ne sme da napravi novi pad.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Korisnik je video izveštaj: više se ne nudi pri pokretanju, ali ostaje u podešavanjima. */
    fun markSeen(context: Context) {
        File(File(context.filesDir, DIR), FRESH_MARK).delete()
        _report.value = _report.value?.copy(fresh = false)
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
        _report.value = null
    }

    private fun write(dir: File, text: String) {
        dir.mkdirs()
        File(dir, FILE).writeText(text)
        File(dir, FRESH_MARK).createNewFile()
    }

    private fun read(dir: File): CrashReport? = try {
        val file = File(dir, FILE)
        if (!file.isFile) null else CrashReport(file.lastModified(), file.readText(), File(dir, FRESH_MARK).exists())
    } catch (_: Exception) {
        null
    }

    private fun deviceInfo(): List<String> = listOf(
        "Glowbox ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
        "${Build.MANUFACTURER} ${Build.MODEL}",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )

    /** Tekst izveštaja: vreme, podaci o aplikaciji i uređaju, pa trag steka bez adresa. */
    fun format(timeMs: Long, threadName: String, error: Throwable, info: List<String>): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        // SimpleDateFormat, jer java.time ne postoji na Androidu 7 (minSdk 24).
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(timeMs))
        return buildString {
            appendLine(time)
            info.forEach { appendLine(it) }
            appendLine("Thread: $threadName")
            appendLine()
            append(redact(trace).take(MAX_CHARS))
        }
    }

    private val URL = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>)\]]+""")

    /**
     * Uklanja adrese iz teksta. Poruka greške često nosi adresu strima, a ona kod Xtream lista
     * sadrži korisničko ime i lozinku, pa ne sme da završi u izveštaju koji se deli.
     */
    fun redact(text: String): String = URL.replace(text, "[url]")
}
