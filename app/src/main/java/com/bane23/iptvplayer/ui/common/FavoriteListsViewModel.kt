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

/** Kanal za koji je otvoren izbor liste omiljenih, sa onim gde se sada nalazi. */
data class FavoritePick(
    val channelId: Long,
    val channelName: String,
    /** Kanal je u samim „Omiljenima” (nezavisno od imenovanih lista). */
    val favorite: Boolean,
    /** Imenovane liste u kojima je kanal. */
    val listIds: Set<Long>,
)

/**
 * Liste omiljenih kanala sa imenom: izbor liste pri dodavanju kanala u omiljene i pravljenje,
 * preimenovanje i brisanje lista. Dok nijedna imenovana lista ne postoji, dodavanje u omiljene
 * radi jednim potezom, bez pitanja.
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

    /** Kanal za koji je izbor liste otvoren, ili null dok je zatvoren. */
    val pick: StateFlow<FavoritePick?> = _pick.asStateFlow()

    private val _nameTaken = MutableStateFlow(false)

    /** Poslednje uneto ime liste je prazno ili već postoji. */
    val nameTaken: StateFlow<Boolean> = _nameTaken.asStateFlow()

    /**
     * Potez „omiljeno” nad kanalom. Bez imenovanih lista kanal se odmah dodaje ili uklanja; kad
     * liste postoje, otvara se izbor liste.
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

    /** Dodaje kanal iz izbora u „Omiljene” ili ga uklanja iz njih; imenovane liste se ne menjaju. */
    fun toggleFavorite() = change { pick ->
        repository.toggleFavorite(pick.channelId)
    }

    /** Stavlja kanal iz izbora u listu [listId] ili ga vadi iz nje. */
    fun toggleList(listId: Long) = change { pick ->
        repository.setInFavoriteList(pick.channelId, listId, listId !in pick.listIds)
    }

    /** Pravi listu [name]; ako je izbor otvoren, kanal odmah ide u nju. [onDone] javlja uspeh. */
    fun create(name: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createFavoriteList(name)
            _nameTaken.value = id == null
            val pick = _pick.value
            if (id != null && pick != null) {
                repository.setInFavoriteList(pick.channelId, id, true)
                _pick.value = _pick.value?.let { load(it.channelId) }
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

    /** Briše listu; kanali ostaju u ostalim listama u kojima su bili. */
    fun delete(listId: Long) {
        viewModelScope.launch { repository.deleteFavoriteList(listId) }
    }

    fun clearNameError() {
        _nameTaken.value = false
    }

    private fun change(action: suspend (FavoritePick) -> Unit) {
        val pick = _pick.value ?: return
        viewModelScope.launch {
            action(pick)
            _pick.value = _pick.value?.let { load(it.channelId) }
        }
    }

    private suspend fun load(channelId: Long): FavoritePick? {
        val channel = repository.getChannel(channelId) ?: return null
        return FavoritePick(
            channelId = channelId,
            channelName = channel.name,
            favorite = repository.isFavorite(channelId),
            listIds = repository.favoriteListsOf(channelId),
        )
    }
}
