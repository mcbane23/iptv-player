package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.ChannelEntity
import com.bane23.iptvplayer.data.db.PlaylistEntity
import com.bane23.iptvplayer.data.m3u.Catchup
import com.bane23.iptvplayer.data.xtream.XtreamApi
import com.bane23.iptvplayer.data.xtream.XtreamArchiveParser
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.domain.ArchiveProgram
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.domain.StreamSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/**
 * Arhiva (catch-up): spisak emisija kanala koje su već prošle i adrese za njihovo gledanje.
 * Za Xtream spisak daje server; za M3U se čita iz vodiča koji je sačuvan na uređaju.
 */
@Singleton
class ArchiveRepository @Inject constructor(
    private val db: AppDatabase,
    private val api: XtreamApi,
    private val cipher: PasswordCipher,
) {
    private class XtreamAccess(val server: HttpUrl, val username: String, val password: String)

    /** Završene emisije kanala, najnovije prvo. Baca [IOException] ako server ne odgovori. */
    suspend fun programs(channelId: Long): List<ArchiveProgram> = withContext(Dispatchers.IO) {
        val channel = db.channelDao().getById(channelId) ?: return@withContext emptyList()
        val playlist = db.playlistDao().getById(channel.playlistId) ?: return@withContext emptyList()
        val days = archiveDays(channel) ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        val earliest = now - days * DAY_MS
        val streamId = channel.xtreamStreamId
        if (playlist.type == PlaylistType.XTREAM && streamId != null) {
            val access = playlist.xtreamAccess() ?: throw IOException("Account can not be read")
            val url = XtreamUrls.playerApi(
                access.server, access.username, access.password,
                XtreamUrls.ACTION_ARCHIVE_GUIDE, "stream_id", streamId.toString(),
            )
            val response = try {
                api.details(url, playlist.userAgent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Poruka izuzetka može da sadrži adresu sa lozinkom, zato se ne prenosi dalje.
                throw IOException("Archive guide request failed: ${e.javaClass.simpleName}")
            }
            XtreamArchiveParser.parse(response, earliest, now).map {
                ArchiveProgram(it.startMs, it.stopMs, it.title, it.description, it.startToken)
            }
        } else {
            val key = channel.epgKey ?: return@withContext emptyList()
            db.epgDao().range(channel.playlistId, key, earliest, now)
                .filter { it.stopMs <= now && it.startMs >= earliest }
                .sortedByDescending { it.startMs }
                .map { ArchiveProgram(it.startMs, it.stopMs, it.title, it.description) }
        }
    }

    /** Adrese za gledanje emisije iz arhive, ili null ako ne mogu da se sastave. */
    suspend fun source(channelId: Long, startMs: Long, stopMs: Long, startToken: String?): StreamSource? =
        withContext(Dispatchers.IO) {
            val channel = db.channelDao().getById(channelId) ?: return@withContext null
            val playlist = db.playlistDao().getById(channel.playlistId) ?: return@withContext null
            val streamId = channel.xtreamStreamId
            val urls = if (playlist.type == PlaylistType.XTREAM && streamId != null) {
                val access = playlist.xtreamAccess() ?: return@withContext null
                // Emisija izabrana u vodiču nema početak po satu servera: uzima se iz spiska arhive.
                val token = startToken
                    ?: ArchiveMatch.best(runCatching { programs(channelId) }.getOrDefault(emptyList()), startMs, stopMs)?.startToken
                    ?: return@withContext null
                val minutes = ((stopMs - startMs) + 59_999) / 60_000
                listOf("ts", "m3u8").map { extension ->
                    XtreamUrls.timeshift(access.server, access.username, access.password, streamId, token, minutes, extension)
                }
            } else {
                listOfNotNull(
                    Catchup.url(
                        channel.catchupType, channel.catchupSource, channel.streamUrl,
                        startMs, stopMs, System.currentTimeMillis(),
                    ),
                )
            }
            if (urls.isEmpty()) null else StreamSource(urls, channel.userAgent ?: playlist.userAgent, channel.referrer)
        }

    private fun PlaylistEntity.xtreamAccess(): XtreamAccess? {
        val server = XtreamUrls.normalizeServer(source) ?: return null
        val user = username ?: return null
        val password = try {
            cipher.decrypt(passwordEncrypted ?: return null)
        } catch (_: Exception) {
            return null
        }
        return XtreamAccess(server, user, password)
    }

    companion object {
        const val DAY_MS = 24 * 3_600_000L

        /** Najduža arhiva koja se prikazuje i za koju se vodič čuva na uređaju. */
        const val MAX_DAYS = 7

        /** Oznaka u bazi za kanal čiju arhivu daje Xtream server. */
        const val TYPE_XTREAM = "xtream"

        /** Koliko dana unazad kanal ima arhivu, ili null ako je nema. */
        fun archiveDays(channel: ChannelEntity): Int? {
            val supported = channel.catchupType == TYPE_XTREAM ||
                Catchup.isSupported(channel.catchupType, channel.catchupSource, channel.streamUrl)
            return if (supported) (channel.catchupDays ?: 1).coerceIn(1, MAX_DAYS) else null
        }
    }
}

/** Nalaženje emisije iz vodiča u spisku arhive koji daje server. */
object ArchiveMatch {
    /**
     * Emisija iz [programs] koja se najviše poklapa sa terminom od [startMs] do [stopMs], ili null
     * kad se nijedna ne poklapa bar pola tog termina (vodič i arhiva ne moraju biti iz istog izvora).
     */
    fun best(programs: List<ArchiveProgram>, startMs: Long, stopMs: Long): ArchiveProgram? {
        val length = (stopMs - startMs).coerceAtLeast(1)
        return programs
            .map { it to (minOf(it.stopMs, stopMs) - maxOf(it.startMs, startMs)) }
            .filter { (_, overlap) -> overlap * 2 >= length }
            .maxByOrNull { (_, overlap) -> overlap }
            ?.first
    }
}
