package com.bane23.iptvplayer.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Posteri za pozadinu čuvara ekrana: omiljeni filmovi i serije aktivne liste, a ako ih nema,
 * poslednje gledani. Zaključani naslovi (roditeljska zaštita) se ne prikazuju.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScreensaverViewModel @Inject constructor(
    vod: VodRepository,
    weather: WeatherRepository,
) : ViewModel() {
    /**
     * Trenutna temperatura, ili null dok je nema. Pita se samo dok je čuvar na ekranu, jednom u
     * minutu; spremište samo ide na internet najviše na pola sata.
     */
    val temperature: StateFlow<Int?> = flow {
        while (true) {
            emit(weather.currentTemperature())
            delay(TEMPERATURE_CHECK_MS)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val posters: StateFlow<List<String>> = vod.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    vod.observeFavorites(playlistId, VodKind.MOVIES),
                    vod.observeFavorites(playlistId, VodKind.SERIES),
                    vod.observeContinue(playlistId, VodKind.MOVIES),
                    vod.observeContinue(playlistId, VodKind.SERIES),
                ) { movies, series, continueMovies, continueSeries ->
                    screensaverPosters(
                        favorites = movies.map { it.poster } + series.map { it.poster },
                        recent = (continueMovies + continueSeries).sortedByDescending { it.updatedAt }.map { it.poster },
                    )
                }
            }
        }
        // Bez postera čuvar ostaje crn, kao ranije.
        .catch { if (it is CancellationException) throw it else emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private const val TEMPERATURE_CHECK_MS = 60_000L

/** Omiljeni posteri; ako ih nema, poslednje gledani. Bez praznih i ponovljenih adresa. */
internal fun screensaverPosters(favorites: List<String?>, recent: List<String?>): List<String> {
    fun List<String?>.usable() = mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    return favorites.usable().ifEmpty { recent.usable() }
}
