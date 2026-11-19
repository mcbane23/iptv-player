package com.bane23.iptvplayer.data.download

import com.bane23.iptvplayer.domain.StreamSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Server je odbio da pošalje fajl; ponovni pokušaj ne bi pomogao. */
class DownloadRefusedException(code: Int) : IOException("HTTP $code")

/** Preuzima fajl sa servera na uređaj i ume da nastavi tamo gde je stao. */
@Singleton
class FileDownloader @Inject constructor(private val client: OkHttpClient) {

    /**
     * Preuzima u [file]; ako deo fajla već postoji, traži od servera samo ostatak. [onProgress]
     * dobija koliko je preuzeto i veličinu celog fajla (0 dok se ne zna); kad vrati false,
     * preuzimanje staje. Vraća true kad je fajl ceo. Baca [IOException] kad se veza prekine.
     */
    suspend fun download(source: StreamSource, file: File, onProgress: suspend (bytes: Long, total: Long) -> Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val url = source.urls.firstOrNull() ?: throw DownloadRefusedException(0)
            // Najviše dva kruga: drugi je preuzimanje ispočetka kad deo na uređaju ne odgovara serveru.
            repeat(2) {
                val existing = file.length()
                val result = attempt(url, source, file, existing, onProgress)
                if (result != null) return@withContext result
                file.delete()
            }
            throw IOException("File on the server keeps changing")
        }

    /** Vraća null kad fajl treba obrisati i tražiti ispočetka. */
    private suspend fun attempt(
        url: String,
        source: StreamSource,
        file: File,
        existing: Long,
        onProgress: suspend (Long, Long) -> Boolean,
    ): Boolean? = coroutineScope {
        val request = try {
            Request.Builder().url(url).apply {
                source.userAgent?.let { header("User-Agent", it) }
                source.referrer?.let { header("Referer", it) }
                if (existing > 0) header("Range", "bytes=$existing-")
            }.build()
        } catch (_: IllegalArgumentException) {
            throw DownloadRefusedException(0)
        }
        val call = client.newCall(request)
        // Čitanje sa mreže ne ume da se otkaže samo: kad se korutina otkaže, prekida se veza.
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                val length = response.body.contentLength()
                val plan = DownloadRules.resumePlan(existing, response.code, length, response.header("Content-Range"))
                when (plan) {
                    null -> {
                        // Greške servera (5xx) i zauzeta veza umeju da prođu same; ostalo je odbijanje.
                        if (response.code >= 500 || response.code == 429) throw IOException("HTTP ${response.code}")
                        throw DownloadRefusedException(response.code)
                    }
                    ResumePlan.Complete -> true
                    ResumePlan.StartOver -> null
                    is ResumePlan.Append -> copy(response.body.byteStream(), file, append = true, existing, plan.totalBytes, onProgress)
                    is ResumePlan.Restart -> copy(response.body.byteStream(), file, append = false, 0, plan.totalBytes, onProgress)
                }
            }
        } catch (e: IOException) {
            // Prekid veze zbog otkazivanja nije greška mreže.
            ensureActive()
            throw e
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun copy(
        input: java.io.InputStream,
        file: File,
        append: Boolean,
        start: Long,
        total: Long,
        onProgress: suspend (Long, Long) -> Boolean,
    ): Boolean {
        var written = start
        FileOutputStream(file, append).use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                written += count
                if (!onProgress(written, total)) return false
            }
        }
        // Veza se zatvorila pre kraja fajla: preuzimanje se nastavlja novim zahtevom.
        if (total > 0 && written < total) throw IOException("Connection closed early")
        return true
    }

    private companion object {
        const val BUFFER_BYTES = 128 * 1024
    }
}
