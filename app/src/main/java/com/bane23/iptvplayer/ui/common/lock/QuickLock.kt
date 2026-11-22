package com.bane23.iptvplayer.ui.common.lock

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stavka roditeljske zaštite u meniju koji se otvara dugim pritiskom (dugim OK) na kategoriju. */
enum class LockAction(@StringRes val label: Int) {
    /** Zaključava ovu kategoriju. */
    LOCK(R.string.lock_action_lock),

    /** Skida zaključavanje sa ove kategorije; nudi se samo dok je PIN unet. */
    REMOVE(R.string.lock_action_remove),

    /** Traži PIN i otključava sve zaključano, do izlaska iz aplikacije. */
    UNLOCK_ALL(R.string.lock_action_unlock_all),

    /** Ponovo zaključava sve što je PIN-om otključano. */
    LOCK_AGAIN(R.string.parental_lock_again),
}

data class QuickLockState(
    val hasPin: Boolean = false,
    /** PIN je unet u ovom pokretanju aplikacije. */
    val unlocked: Boolean = false,
    /** Sve što je izabrano za zaključavanje, bez obzira na to da li je PIN sada unet. */
    val lockedKeys: Set<String> = emptySet(),
    val playlistId: Long? = null,
) {
    /**
     * Stavke za meni sadržaja sa ključem [key] (null kad taj sadržaj ne može da se zaključa). Novo
     * zaključavanje ne traži PIN; skidanje zaključavanja se nudi samo dok je PIN unet.
     */
    fun actions(key: String?): List<LockAction> = buildList {
        if (key != null) {
            if (key !in lockedKeys) add(LockAction.LOCK) else if (unlocked) add(LockAction.REMOVE)
        }
        if (hasPin && lockedKeys.isNotEmpty()) add(if (unlocked) LockAction.LOCK_AGAIN else LockAction.UNLOCK_ALL)
    }

    /** Stavke za meni pojedinačnog kanala, filma ili serije: samo zaključavanje tog sadržaja. */
    fun itemActions(key: String?): List<LockAction> =
        actions(key).filter { it == LockAction.LOCK || it == LockAction.REMOVE }

    fun channelKey(channel: Channel): String? =
        channel.stableKey.takeIf { it.isNotEmpty() }?.let { LockRepository.itemKey(LockRepository.KIND_CHANNEL, channel.playlistId, it) }

    /** Ključ filma ([series] = false) ili serije aktivne liste. */
    fun vodItemKey(series: Boolean, itemId: Long): String? = playlistId?.let {
        LockRepository.itemKey(if (series) LockRepository.KIND_SERIES else LockRepository.KIND_MOVIE, it, itemId.toString())
    }

    fun liveKey(category: Category): String =
        LockRepository.liveKey(category.playlistId, category.name.removePrefix(LockRepository.LOCK_MARK))

    /** Ključ kategorije filmova ([series] = false) ili serija aktivne liste. */
    fun vodKey(series: Boolean, categoryId: String): String? =
        playlistId?.let { LockRepository.vodKey(it, series, categoryId) }
}

@HiltViewModel
class QuickLockViewModel @Inject constructor(
    private val locks: LockRepository,
    channels: ChannelRepository,
) : ViewModel() {
    val state: StateFlow<QuickLockState> =
        combine(locks.hasPin, locks.unlocked, locks.lockedKeys, channels.activePlaylistId) { hasPin, unlocked, keys, playlistId ->
            QuickLockState(hasPin, unlocked, keys, playlistId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, QuickLockState())

    fun setLocked(key: String, locked: Boolean, name: String = "") {
        viewModelScope.launch { locks.setLocked(key, locked, name) }
    }

    fun lockAgain() = locks.lockAgain()
}

/** Stavke roditeljske zaštite za menije na jednom ekranu i njihovo izvršavanje. */
class LockMenu(val state: QuickLockState, private val perform: (action: LockAction, key: String?, name: String) -> Unit) {
    fun actions(key: String?): List<LockAction> = state.actions(key)

    /** [name] je naziv pojedinačnog kanala, filma ili serije; kategoriji ne treba. */
    fun run(action: LockAction, key: String?, name: String = "") = perform(action, key, name)
}

/** Stavke zaključavanja jednog kanala, filma ili serije i njihovo izvršavanje. */
class ItemLock(val actions: List<LockAction>, val run: (LockAction) -> Unit)

/** Null kad kanal ne može da se zaključa ili za njega sada nema nijedne stavke. */
fun LockMenu.channel(channel: Channel): ItemLock? = item(state.channelKey(channel), channel.name)

/** Film ([series] = false) ili serija aktivne liste. */
fun LockMenu.vodItem(series: Boolean, itemId: Long, name: String): ItemLock? = item(state.vodItemKey(series, itemId), name)

private fun LockMenu.item(key: String?, name: String): ItemLock? {
    val actions = state.itemActions(key)
    return if (actions.isEmpty()) null else ItemLock(actions) { run(it, key, name) }
}

/** [onUnlock] otvara unos PIN-a koji otključava sve. */
@Composable
fun rememberLockMenu(onUnlock: () -> Unit = {}, viewModel: QuickLockViewModel = hiltViewModel()): LockMenu {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val unlock by rememberUpdatedState(onUnlock)
    return remember(state) {
        LockMenu(state) { action, key, name ->
            when (action) {
                LockAction.LOCK -> when {
                    key == null -> Unit

                    !state.hasPin -> AppMessages.show(context, R.string.lock_needs_pin, long = true)

                    else -> {
                        viewModel.setLocked(key, true, name)
                        // Dok je PIN unet, novo zaključavanje se vidi tek kad se ponovo zaključa.
                        AppMessages.show(context, if (state.unlocked) R.string.lock_done_open else R.string.parental_locked, long = state.unlocked)
                    }
                }

                LockAction.REMOVE -> if (key != null) {
                    viewModel.setLocked(key, false)
                    AppMessages.show(context, R.string.lock_removed)
                }

                LockAction.UNLOCK_ALL -> unlock()

                LockAction.LOCK_AGAIN -> {
                    viewModel.lockAgain()
                    AppMessages.show(context, R.string.parental_locked)
                }
            }
        }
    }
}
