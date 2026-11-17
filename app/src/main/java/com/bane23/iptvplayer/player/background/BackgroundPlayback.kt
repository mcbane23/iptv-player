package com.bane23.iptvplayer.player.background

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puštanje u pozadini na telefonu: plejer koji sme da nastavi kad se ekran ugasi. Ekran plejera
 * ga prijavljuje kad prestane da bude u prvom planu, a [PlaybackService] drži aplikaciju živom i
 * prikazuje obaveštenje sa pauzom. Koristi se samo sa glavne niti.
 */
@OptIn(UnstableApi::class)
object BackgroundPlayback {

    class Target(
        val player: ExoPlayer,
        val title: String,
        /** Pauza i puštanje, isto kao dugme u plejeru. */
        val toggle: () -> Unit,
        /** Zaustavlja puštanje i oslobađa vezu sa serverom. */
        val stop: () -> Unit,
    )

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    val active: Boolean get() = _target.value != null

    /** Vraća false ako sistem ne dozvoli pokretanje servisa; tada se plejer zaustavlja kao i do sada. */
    fun start(context: Context, target: Target): Boolean {
        _target.value = target
        return try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            // Procesor i Wi-Fi ostaju budni dok se pušta sa ugašenim ekranom.
            target.player.setWakeMode(C.WAKE_MODE_NETWORK)
            true
        } catch (_: IllegalStateException) {
            _target.value = null
            false
        } catch (_: SecurityException) {
            _target.value = null
            false
        }
    }

    /** Servis se sam gasi čim vidi da više nema plejera. */
    fun stop() {
        val current = _target.value ?: return
        _target.value = null
        current.player.setWakeMode(C.WAKE_MODE_NONE)
    }
}
