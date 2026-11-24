package com.bane23.iptvplayer.data.trakt

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Šta se desilo sa jednom pozicijom pri razmeni sa Trakt-om (za dnevnik u podešavanjima). */
enum class PlaybackLogEvent {
    /** Poslato na Trakt; [PlaybackLogEntry.count] je HTTP odgovor. */
    SENT,
    SEND_NO_TMDB,
    SEND_PANEL_WAIT,

    /** Spisak sa Trakt-a: [PlaybackLogEntry.count] ukupno, [PlaybackLogEntry.percent] novih. */
    RECEIVED_LIST,

    /** Upisano; [PlaybackLogEntry.count] je broj verzija. */
    APPLIED,
    SKIP_NO_ITEM,
    SKIP_NO_EPISODE,
    SKIP_NEWER_HERE,
    SKIP_SAME,
    SKIP_FINISHED,
    SKIP_DISMISSED,
    SKIP_NO_DURATION,
    SKIP_PANEL,
    FAILED,

    /** Plejer je krenuo od [PlaybackLogEntry.count] sekundi; [PlaybackLogEntry.percent]: 1 razmena gotova, 0 nije, -1 bez Trakt-a. */
    PLAYER_START,
}

@Serializable
data class PlaybackLogEntry(
    val atMs: Long,
    val event: PlaybackLogEvent,
    val item: String = "",
    val percent: Int = 0,
    val count: Int = 0,
    val detail: String = "",
)

/**
 * Kratak dnevnik razmene pozicija sa Trakt-om (poslednjih [MAX] događaja), da se vidi gde se pozicija
 * gubi između uređaja. Čuva se u fajlu aplikacije i ide i u logcat (oznaka [TAG]).
 */
@Singleton
class TraktPlaybackLog @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "trakt-playback-log.json")
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<PlaybackLogEntry>> = _entries.asStateFlow()

    fun add(event: PlaybackLogEvent, item: String = "", percent: Int = 0, count: Int = 0, detail: String = "") {
        val entry = PlaybackLogEntry(System.currentTimeMillis(), event, item, percent, count, detail)
        Log.i(TAG, "$event item=$item percent=$percent count=$count $detail")
        synchronized(this) {
            val next = (listOf(entry) + _entries.value).take(MAX)
            _entries.value = next
            runCatching { file.writeText(TraktJson.encodeToString(next)) }
        }
    }

    private fun read(): List<PlaybackLogEntry> =
        runCatching { TraktJson.decodeFromString<List<PlaybackLogEntry>>(file.readText()) }.getOrDefault(emptyList())

    companion object {
        const val TAG = "GlowboxTrakt"
        private const val MAX = 30

        /** Oznaka stavke u dnevniku: TMDB broj, a za epizodu i sezona i broj. */
        fun label(key: TraktKey): String =
            if (key.kind == TraktKey.MOVIE) "TMDB ${key.tmdbId}" else "TMDB ${key.tmdbId} S${key.season}E${key.episode}"
    }
}
