package com.bane23.iptvplayer.data.recording

import com.bane23.iptvplayer.domain.StreamSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Strim ne može da se snimi nadovezivanjem (šifrovan je ili nije MPEG-TS). */
class UnsupportedStreamException : IOException("Unsupported stream")

/**
 * Snima strim uživo u fajl: MPEG-TS preko HTTP-a se prepisuje kako stiže, a HLS se snima deo po deo.
 * Koristi sopstvenu vezu ka serveru, nezavisnu od plejera.
 */
@Singleton
class StreamRecorder @Inject constructor(client: OkHttpClient) {

    // Strim uživo nema kraj, pa se čeka samo na sledeći paket podataka.
    private val client = client.newBuilder().readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS).build()

    /**
     * Snima dok se korutina ne otkaže ili strim ne završi. Podaci se dodaju na kraj [file].
     * [onProgress] povremeno dobija veličinu fajla; kad vrati false, snimanje se uredno završava.
     * Baca [IOException] kad strim ne može da se otvori ili se prekine i ne vrati.
     */
    suspend fun record(source: StreamSource, file: File, onProgress: (Long) -> Boolean) = withContext(Dispatchers.IO) {
        FileOutputStream(file, true).use { output ->
            var written = file.length()
            var failures = 0
            var urlIndex = 0
            var lastError: IOException? = null
            while (true) {
                ensureActive()
                val url = source.urls.getOrNull(urlIndex) ?: throw lastError ?: IOException("No stream address")
                val before = written
                try {
                    val finished = recordUrl(url, source) { bytes, offset, count ->
                        output.write(bytes, offset, count)
                        written += count
                        onProgress(written)
                    }
                    if (finished) return@use
                    throw IOException("Stream ended")
                } catch (e: UnsupportedStreamException) {
                    throw e
                } catch (e: IOException) {
                    ensureActive()
                    lastError = e
                    if (written > before) {
                        // Strim je radio pa se prekinuo: ista adresa se proba ponovo.
                        failures = 0
                    } else if (written == 0L && urlIndex < source.urls.lastIndex) {
                        // Ništa nije stiglo: proba se sledeća adresa (npr. HLS umesto MPEG-TS).
                        urlIndex++
                        continue
                    }
                    if (++failures > MAX_FAILURES) throw e
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    /** Vraća true kad je snimanje uredno gotovo (strim se završio ili [write] više ne želi podatke). */
    private suspend fun recordUrl(url: String, source: StreamSource, write: (ByteArray, Int, Int) -> Boolean): Boolean =
        open(url, source) { response ->
            val body = response.body.source()
            val isPlaylist = looksLikePlaylist(url, response) ||
                (body.request(PLAYLIST_MARK.length.toLong()) && HlsPlaylist.isPlaylist(body.peek().readUtf8(PLAYLIST_MARK.length.toLong())))
            if (isPlaylist) return@open Opened.Playlist(body.readUtf8(), response.request.url.toString())
            val buffer = ByteArray(BUFFER_BYTES)
            var finished = false
            while (true) {
                val count = body.read(buffer)
                if (count < 0) break
                if (count > 0 && !write(buffer, 0, count)) {
                    finished = true
                    break
                }
            }
            Opened.Done(finished)
        }.let { opened ->
            when (opened) {
                is Opened.Done -> opened.finished
                is Opened.Playlist -> recordHls(opened.text, opened.url, source, write)
            }
        }

    private suspend fun recordHls(
        firstText: String,
        firstUrl: String,
        source: StreamSource,
        write: (ByteArray, Int, Int) -> Boolean,
    ): Boolean {
        var playlistUrl = firstUrl
        var playlist = HlsPlaylist.parse(firstText, firstUrl)
        if (playlist.variants.isNotEmpty()) {
            playlistUrl = playlist.variants.first()
            playlist = fetchPlaylist(playlistUrl, source)
        }
        var nextSequence = -1L
        var idleRounds = 0
        while (true) {
            if (playlist.unsupported || playlist.variants.isNotEmpty()) throw UnsupportedStreamException()
            val fresh = if (nextSequence < 0) {
                // Snimanje kreće od onoga što se sada emituje, ne od početka prozora.
                playlist.segments.takeLast(START_SEGMENTS)
            } else {
                playlist.segments.filter { it.sequence >= nextSequence }
            }
            for (segment in fresh) {
                val keepGoing = open(segment.url, source) { response ->
                    val body = response.body.source()
                    val buffer = ByteArray(BUFFER_BYTES)
                    var wanted = true
                    while (wanted) {
                        val count = body.read(buffer)
                        if (count < 0) break
                        if (count > 0) wanted = write(buffer, 0, count)
                    }
                    wanted
                }
                if (!keepGoing) return true
                nextSequence = segment.sequence + 1
            }
            if (playlist.ended) return true
            idleRounds = if (fresh.isEmpty()) idleRounds + 1 else 0
            if (idleRounds > MAX_IDLE_ROUNDS) throw IOException("No new segments")
            delay((playlist.targetDurationSeconds * 1000L / 2).coerceIn(MIN_POLL_MS, MAX_POLL_MS))
            playlist = fetchPlaylist(playlistUrl, source)
        }
    }

    private suspend fun fetchPlaylist(url: String, source: StreamSource): HlsPlaylist =
        open(url, source) { response -> HlsPlaylist.parse(response.body.string(), response.request.url.toString()) }

    /** Otvara adresu i izvršava [block] nad odgovorom. Otkazivanje korutine prekida i čitanje sa mreže. */
    private suspend fun <T> open(url: String, source: StreamSource, block: (Response) -> T): T = coroutineScope {
        val request = Request.Builder().url(url).apply {
            source.userAgent?.let { header("User-Agent", it) }
            source.referrer?.let { header("Referer", it) }
        }.build()
        val call = client.newCall(request)
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                block(response)
            }
        } catch (e: IllegalArgumentException) {
            throw IOException("Bad address", e)
        } finally {
            watcher.cancel()
        }
    }

    private fun looksLikePlaylist(url: String, response: Response): Boolean {
        val type = response.header("Content-Type").orEmpty().lowercase()
        return "mpegurl" in type || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
    }

    private sealed interface Opened {
        data class Done(val finished: Boolean) : Opened
        data class Playlist(val text: String, val url: String) : Opened
    }

    private companion object {
        const val READ_TIMEOUT_SECONDS = 30L
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_FAILURES = 5
        const val RETRY_DELAY_MS = 3_000L
        const val START_SEGMENTS = 2
        const val MAX_IDLE_ROUNDS = 20
        const val MIN_POLL_MS = 1_000L
        const val MAX_POLL_MS = 10_000L
        const val PLAYLIST_MARK = "#EXTM3U"
    }
}
