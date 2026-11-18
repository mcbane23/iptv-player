package com.bane23.iptvplayer.data.watchnext

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.bane23.iptvplayer.MainActivity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.repo.mergeContinue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Film ili serija iz reda „Nastavite sa gledanjem” na početnom ekranu TV-a, onako kako stiže nazad
 * u aplikaciju kad ga korisnik izabere. Otvara ekran detalja, odakle se nastavlja jednim klikom.
 */
data class WatchNextLink(
    val playlistId: Long,
    val series: Boolean,
    val itemId: Long,
    val name: String,
    val poster: String?,
    val extension: String?,
) {
    /** Jedinstvena oznaka stavke u redu na početnom ekranu. */
    val key: String get() = "$playlistId|${if (series) KIND_SERIES else KIND_MOVIE}|$itemId"

    fun toUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendQueryParameter(PARAM_PLAYLIST, playlistId.toString())
        .appendQueryParameter(PARAM_KIND, if (series) KIND_SERIES else KIND_MOVIE)
        .appendQueryParameter(PARAM_ID, itemId.toString())
        .appendQueryParameter(PARAM_NAME, name)
        .apply {
            if (poster != null) appendQueryParameter(PARAM_POSTER, poster)
            if (extension != null) appendQueryParameter(PARAM_EXTENSION, extension)
        }
        .build()

    companion object {
        const val SCHEME = "iptvplayer"
        const val HOST = "watch"
        private const val PARAM_PLAYLIST = "playlist"
        private const val PARAM_KIND = "kind"
        private const val PARAM_ID = "id"
        private const val PARAM_NAME = "name"
        private const val PARAM_POSTER = "poster"
        private const val PARAM_EXTENSION = "ext"
        private const val KIND_MOVIE = "movie"
        private const val KIND_SERIES = "series"

        /** Čita link iz namere kojom je aktivnost pokrenuta; null kad to nije link ovog reda. */
        fun of(intent: Intent?): WatchNextLink? {
            val data = intent?.data ?: return null
            if (intent.action != Intent.ACTION_VIEW || data.scheme != SCHEME || data.host != HOST) return null
            val kind = data.getQueryParameter(PARAM_KIND)
            if (kind != KIND_MOVIE && kind != KIND_SERIES) return null
            return WatchNextLink(
                playlistId = data.getQueryParameter(PARAM_PLAYLIST)?.toLongOrNull() ?: return null,
                series = kind == KIND_SERIES,
                itemId = data.getQueryParameter(PARAM_ID)?.toLongOrNull() ?: return null,
                name = data.getQueryParameter(PARAM_NAME).orEmpty(),
                poster = data.getQueryParameter(PARAM_POSTER)?.takeIf { it.isNotBlank() },
                extension = data.getQueryParameter(PARAM_EXTENSION)?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/**
 * Drži red „Nastavite sa gledanjem” na početnom ekranu Android TV / Google TV uređaja u skladu sa
 * redom „Nastavi gledanje” aktivne liste: započeti filmovi i serije se dodaju, a odgledani i
 * sklonjeni uklanjaju. Na uređajima bez tog reda (telefoni, Fire TV) ne radi ništa.
 */
@Singleton
class WatchNextSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vod: VodRepository,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Počinje da prati „Nastavi gledanje”. Poziva se jednom, pri pokretanju aplikacije na TV-u. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        if (started || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        started = true
        scope.launch {
            combine(vod.activePlaylistId, settings.watchNext) { playlistId, enabled -> playlistId.takeIf { enabled } }
                .flatMapLatest { playlistId ->
                    if (playlistId == null) {
                        flowOf(emptyList<WatchNextLink>() to emptyMap())
                    } else {
                        combine(
                            vod.observeContinue(playlistId, VodKind.MOVIES),
                            vod.observeContinue(playlistId, VodKind.SERIES),
                        ) { movies, series ->
                            val items = mergeContinue(movies, series, LIMIT)
                            items.map { it.toLink(playlistId) } to items.associateBy { it.toLink(playlistId).key }
                        }
                    }
                }
                // Pozicija se tokom gledanja upisuje često; početni ekran ne mora da je prati u sekundu.
                .debounce(DEBOUNCE_MS)
                .collect { (links, items) ->
                    try {
                        publish(links, items)
                    } catch (_: Exception) {
                        // Uređaj nema ovaj red ili ne dozvoljava upis: aplikacija radi kao i do sada.
                    }
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun publish(links: List<WatchNextLink>, items: Map<String, ContinueItem>) {
        val resolver = context.contentResolver
        val table = TvContract.WatchNextPrograms.CONTENT_URI
        val existing = HashMap<String, Long>()
        resolver.query(
            table,
            arrayOf(TvContract.WatchNextPrograms._ID, TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) existing[cursor.getString(1).orEmpty()] = cursor.getLong(0)
        }
        val wanted = links.associateBy { it.key }
        for ((key, id) in existing) {
            if (key !in wanted) resolver.delete(TvContract.buildWatchNextProgramUri(id), null, null)
        }
        for (link in links) {
            val item = items[link.key] ?: continue
            val values = values(link, item)
            val id = existing[link.key]
            if (id == null) resolver.insert(table, values) else resolver.update(TvContract.buildWatchNextProgramUri(id), values, null, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun values(link: WatchNextLink, item: ContinueItem): ContentValues {
        val open = Intent(Intent.ACTION_VIEW, link.toUri())
            .setComponent(ComponentName(context, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return ContentValues().apply {
            put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, link.key)
            put(
                TvContract.WatchNextPrograms.COLUMN_TYPE,
                if (link.series) TvContract.WatchNextPrograms.TYPE_TV_SERIES else TvContract.WatchNextPrograms.TYPE_MOVIE,
            )
            put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, item.updatedAt)
            put(TvContract.WatchNextPrograms.COLUMN_TITLE, item.name)
            if (item.poster != null) {
                put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, item.poster)
                put(
                    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                    POSTER_ASPECT_2_3,
                )
            }
            if (item.durationMs > 0) {
                put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, item.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                put(
                    TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                    item.positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                )
            }
            put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, open.toUri(Intent.URI_INTENT_SCHEME))
        }
    }

    private fun ContinueItem.toLink(playlistId: Long) =
        WatchNextLink(playlistId, isSeries, id, name, poster, extension)

    private companion object {
        const val LIMIT = 10
        const val DEBOUNCE_MS = 5_000L
    }
}

/**
 * Vrednost sistemske konstante za odnos stranica postera 2:3 (`ASPECT_RATIO_2_3`). Upisana je kao broj
 * jer se konstanta iz Kotlina ne vidi kroz `TvContract.WatchNextPrograms`.
 */
private const val POSTER_ASPECT_2_3 = 4
