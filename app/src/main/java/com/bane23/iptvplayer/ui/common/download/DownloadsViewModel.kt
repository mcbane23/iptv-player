package com.bane23.iptvplayer.ui.common.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.DownloadEntity
import com.bane23.iptvplayer.data.download.DownloadRepository
import com.bane23.iptvplayer.data.download.DownloadRules
import com.bane23.iptvplayer.data.download.DownloadStartError
import com.bane23.iptvplayer.data.download.DownloadState
import com.bane23.iptvplayer.data.download.DownloadStorage
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.xtream.Episode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Jedno preuzimanje kako se prikazuje: zapis iz baze, stanje i trenutni napredak. */
data class DownloadItem(
    val entity: DownloadEntity,
    val state: DownloadState,
    val bytes: Long,
    val totalBytes: Long,
) {
    val id: Long get() = entity.id

    /** Naziv u spisku: film, ili serija sa oznakom epizode. */
    val title: String get() = listOfNotNull(entity.title, entity.subtitle).joinToString(" · ")
    val percent: Int? get() = DownloadRules.percent(bytes, totalBytes)

    /** Udeo preuzetog za traku napretka, ili null dok se veličina ne zna. */
    val fraction: Float? get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    /** Još nije gotovo i nije stalo voljom korisnika: može da se pauzira. */
    val pausable: Boolean
        get() = state != DownloadState.DONE && state != DownloadState.PAUSED && state != DownloadState.FAILED
    val resumable: Boolean get() = state == DownloadState.PAUSED || state == DownloadState.FAILED
    val playable: Boolean get() = state == DownloadState.DONE
}

/**
 * Preuzimanja na telefonu: spisak za ekran „Preuzeto” i stanje pojedinih filmova i epizoda za
 * dugme „Preuzmi” u detaljima.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val items: Flow<List<DownloadItem>> =
        combine(repository.downloads, repository.active, repository.hold) { rows, active, hold ->
            rows.map { row ->
                val running = active?.takeIf { it.id == row.id }
                DownloadItem(
                    entity = row,
                    state = DownloadRules.state(row.status, running != null, hold),
                    bytes = running?.bytes ?: row.sizeBytes,
                    totalBytes = running?.totalBytes?.takeIf { it > 0 } ?: row.totalBytes,
                )
            }
        }

    /** Null dok se spisak prvi put ne učita. */
    val downloads: StateFlow<List<DownloadItem>?> = items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Preuzeti filmovi aktivne liste, po ID-ju filma. */
    val movies: StateFlow<Map<Long, DownloadItem>> = ofKind(settings, DownloadEntity.KIND_MOVIE)

    /** Preuzete epizode aktivne liste, po ID-ju epizode. */
    val episodes: StateFlow<Map<Long, DownloadItem>> = ofKind(settings, DownloadEntity.KIND_EPISODE)

    private fun ofKind(settings: SettingsStore, kind: String): StateFlow<Map<Long, DownloadItem>> =
        combine(items, settings.activePlaylistId) { all, playlistId ->
            all.filter { it.entity.kind == kind && it.entity.playlistId == playlistId }.associateBy { it.entity.itemId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _storage = MutableStateFlow<DownloadStorage?>(null)
    val storage: StateFlow<DownloadStorage?> = _storage.asStateFlow()

    private val _notices = Channel<Int>(Channel.BUFFERED)

    /** Kratke poruke (tekst iz resursa) kad preuzimanje ne može da krene. */
    val notices: Flow<Int> = _notices.receiveAsFlow()

    init {
        // Zauzeće se menja dok se preuzima i posle brisanja.
        viewModelScope.launch { repository.downloads.collect { _storage.value = repository.storage() } }
    }

    fun downloadMovie(movieId: Long, title: String, poster: String?, extension: String) {
        viewModelScope.launch { report(repository.enqueueMovie(movieId, title, poster, extension)) }
    }

    fun downloadEpisode(seriesId: Long, seriesTitle: String, poster: String?, episode: Episode) {
        viewModelScope.launch { report(repository.enqueueEpisode(seriesId, seriesTitle, poster, episode)) }
    }

    private fun report(error: DownloadStartError?) {
        val text = when (error) {
            null -> R.string.download_added
            DownloadStartError.NO_SPACE -> R.string.download_error_space
            DownloadStartError.NO_STORAGE, DownloadStartError.NO_PLAYLIST -> R.string.download_error_failed
        }
        _notices.trySend(text)
    }

    fun pause(id: Long) {
        viewModelScope.launch { repository.pause(id) }
    }

    fun resume(id: Long) {
        viewModelScope.launch { repository.resume(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
