package com.bane23.iptvplayer.ui.common.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.cast.CastMedia
import com.bane23.iptvplayer.cast.CastHub
import com.bane23.iptvplayer.cast.CastKind
import com.bane23.iptvplayer.cast.LanDevice
import com.bane23.iptvplayer.cast.LanPickerState
import com.bane23.iptvplayer.cast.CastState
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Izbor uređaja za cast: Chromecast uređaji i televizori sa ovom aplikacijom u kućnoj mreži. */
@HiltViewModel
class CastPickerViewModel @Inject constructor(private val hub: CastHub) : ViewModel() {
    /** Stanje načina koji se koristi (povezivanje, veza). */
    val state: StateFlow<CastState> = hub.state
    val chromecast: StateFlow<CastState> = hub.chromecast.state
    val tvApp: StateFlow<LanPickerState> = hub.tvApp.picker

    init {
        hub.chromecast.init()
    }

    fun startDiscovery() {
        hub.chromecast.startDiscovery()
        hub.tvApp.startDiscovery()
    }

    fun stopDiscovery() {
        hub.chromecast.stopDiscovery()
        hub.tvApp.stopDiscovery()
        hub.tvApp.cancelPairing()
    }

    fun connectChromecast(routeId: String, media: CastMedia) = hub.connectChromecast(routeId, media)

    fun connectTvApp(device: LanDevice, media: CastMedia) = hub.connectTvApp(device, media)

    fun confirmCode(code: String) = hub.tvApp.confirmCode(code)

    fun cancelPairing() = hub.tvApp.cancelPairing()

    fun play(media: CastMedia) = hub.play(media)
}

/**
 * Daljinski dok se sadržaj pušta na TV-u: pauza, premotavanje, jačina, sledeći i prethodni kanal.
 * Za film ili epizodu pamti dokle se stiglo, da se na telefonu nastavi od istog mesta.
 */
@HiltViewModel
class CastRemoteViewModel @Inject constructor(
    private val sender: CastHub,
    private val channels: ChannelRepository,
    private val locks: LockRepository,
    private val vod: VodRepository,
    settings: SettingsStore,
) : ViewModel() {
    val state: StateFlow<CastState> = sender.state
    val kind: StateFlow<CastKind> = sender.kind

    /** Korak dugmadi napred i nazad, iz podešavanja. */
    val seekStepMs: Long = runBlocking { settings.playerSettings.first() }.seekStepSeconds * 1_000L

    /** Za posebne grupe (favoriti, nedavno gledano): redosled zapamćen pri prvom listanju. */
    private var groupIds: List<Long>? = null
    private var lastSavedAt = 0L

    init {
        viewModelScope.launch {
            sender.state.collect { current ->
                val now = System.currentTimeMillis()
                if (current.playing && now - lastSavedAt >= SAVE_EVERY_MS) saveProgress(current)
            }
        }
    }

    fun togglePlayPause() = sender.togglePlayPause()

    fun seekBy(deltaMs: Long) = sender.seekBy(deltaMs)

    fun seekTo(positionMs: Long) = sender.seekTo(positionMs)

    fun volumeUp() = sender.changeVolume(up = true)

    fun volumeDown() = sender.changeVolume(up = false)

    fun retry() {
        sender.state.value.media?.let { sender.play(it.copy(positionMs = sender.state.value.positionMs)) }
    }

    fun next() = step(1)

    fun previous() = step(-1)

    fun stop() {
        saveProgress(sender.state.value, stopped = true)
        sender.stop()
    }

    private fun step(delta: Int) {
        val media = sender.state.value.media ?: return
        val channelId = media.channelId ?: return
        viewModelScope.launch {
            val current = channels.getChannel(channelId) ?: return@launch
            val target = neighbor(current, media.categoryId, delta) ?: return@launch
            val source = channels.streamSource(target.id) ?: return@launch
            sender.play(
                CastMedia(
                    title = target.name,
                    urls = source.urls,
                    live = true,
                    channelId = target.id,
                    categoryId = media.categoryId,
                    userAgent = source.userAgent,
                    referrer = source.referrer,
                ),
            )
            channels.markWatched(target.id)
        }
    }

    /** Sledeći ili prethodni kanal, uz preskakanje zaključanih kategorija (roditeljska zaštita). */
    private suspend fun neighbor(channel: Channel, categoryId: Long?, delta: Int): Channel? {
        var candidate = rawNeighbor(channel, categoryId, delta)
        var skipped = 0
        while (candidate != null && candidate.id != channel.id && locks.isChannelLocked(candidate.id)) {
            if (++skipped > MAX_LOCKED_SKIP) return null
            candidate = rawNeighbor(candidate, categoryId, delta)
        }
        return candidate?.takeIf { it.id != channel.id }
    }

    private suspend fun rawNeighbor(channel: Channel, categoryId: Long?, delta: Int): Channel? {
        if (categoryId == null || !ChannelGroups.isVirtual(categoryId)) {
            return if (delta > 0) channels.next(channel, categoryId) else channels.previous(channel, categoryId)
        }
        val ids = groupIds ?: channels.groupChannelIds(channel.playlistId, categoryId).also { groupIds = it }
        if (ids.isEmpty()) return null
        val index = ids.indexOf(channel.id)
        return channels.getChannel(ids[(index + delta).mod(ids.size)])
    }

    /** [stopped]: slanje je prekinuto, pa pozicija ide i na Trakt. */
    private fun saveProgress(current: CastState, stopped: Boolean = false) {
        val item = current.media?.vod ?: return
        if (current.durationMs <= 0 || current.positionMs <= 0) return
        lastSavedAt = System.currentTimeMillis()
        vod.saveProgress(
            itemId = item.id,
            seriesId = item.seriesId,
            positionMs = current.positionMs,
            durationMs = current.durationMs,
            title = item.title,
            poster = item.poster,
            extension = item.extension,
            stopped = stopped,
        )
    }

    private companion object {
        const val SAVE_EVERY_MS = 10_000L
        const val MAX_LOCKED_SKIP = 300
    }
}
