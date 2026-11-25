package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.domain.FavoriteList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Kanal za koji je otvoren izbor sekcije omiljenih, sa onim gde se sada nalazi. */
data class FavoritePick(
    val channelId: Long,
    val channelName: String,
    /** Kanal je već među omiljenima. */
    val favorite: Boolean,
    /** Sekcija u kojoj je kanal, ili null kad je bez sekcije. */
    val sectionId: Long?,
)

/**
 * Sekcije omiljenih kanala: izbor sekcije pri dodavanju kanala u omiljene i pravljenje,
 * preimenovanje, pomeranje i brisanje sekcija. Dok nijedna sekcija ne postoji, dodavanje u omiljene
 * radi jednim potezom, bez pitanja. (Ime je ostalo od imenovanih lista omiljenih.)
 */
@HiltViewModel
class FavoriteListsViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val lists: StateFlow<List<FavoriteList>> = repository.activePlaylistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeFavoriteLists(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pick = MutableStateFlow<FavoritePick?>(null)

    /** Kanal za koji je izbor sekcije otvoren, ili null dok je zatvoren. */
    val pick: StateFlow<FavoritePick?> = _pick.asStateFlow()

    private val _nameTaken = MutableStateFlow(false)

    /** Poslednje uneto ime sekcije je prazno ili već postoji. */
    val nameTaken: StateFlow<Boolean> = _nameTaken.asStateFlow()

    /**
     * Potez „omiljeno” nad kanalom. Bez sekcija kanal se odmah dodaje ili uklanja; kad sekcije postoje,
     * otvara se izbor sekcije (i uklanjanje, ako je kanal već među omiljenima).
     */
    fun favoriteAction(channelId: Long) {
        viewModelScope.launch {
            if (repository.hasFavoriteLists()) {
                _nameTaken.value = false
                _pick.value = load(channelId)
            } else {
                repository.toggleFavorite(channelId)
            }
        }
    }

    fun closePick() {
        _pick.value = null
    }

    /** Stavlja kanal iz izbora u sekciju [sectionId] (null = bez sekcije) i zatvara izbor. */
    fun choose(sectionId: Long?) {
        val pick = _pick.value ?: return
        _pick.value = null
        viewModelScope.launch { repository.setFavoriteSection(pick.channelId, sectionId) }
    }

    /** Uklanja kanal iz izbora iz omiljenih i zatvara izbor. */
    fun remove() {
        val pick = _pick.value ?: return
        _pick.value = null
        viewModelScope.launch { if (repository.isFavorite(pick.channelId)) repository.toggleFavorite(pick.channelId) }
    }

    /** Pravi sekciju [name]; ako je izbor otvoren, kanal odmah ide u nju i izbor se zatvara. [onDone] javlja uspeh. */
    fun create(name: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createFavoriteList(name)
            _nameTaken.value = id == null
            val pick = _pick.value
            if (id != null && pick != null) {
                _pick.value = null
                repository.setFavoriteSection(pick.channelId, id)
            }
            onDone(id != null)
        }
    }

    fun rename(listId: Long, name: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.renameFavoriteList(listId, name)
            _nameTaken.value = !ok
            onDone(ok)
        }
    }

    /** Pomera sekciju za jedno mesto gore ([delta] = -1) ili dole (1). */
    fun move(listId: Long, delta: Int) {
        viewModelScope.launch { repository.moveFavoriteList(listId, delta) }
    }

    /** Briše sekciju; njeni kanali ostaju među omiljenima, bez sekcije. */
    fun delete(listId: Long) {
        viewModelScope.launch { repository.deleteFavoriteList(listId) }
    }

    fun clearNameError() {
        _nameTaken.value = false
    }

    private suspend fun load(channelId: Long): FavoritePick? {
        val channel = repository.getChannel(channelId) ?: return null
        return FavoritePick(
            channelId = channelId,
            channelName = channel.name,
            favorite = repository.isFavorite(channelId),
            sectionId = repository.favoriteSectionOf(channelId),
        )
    }
}
