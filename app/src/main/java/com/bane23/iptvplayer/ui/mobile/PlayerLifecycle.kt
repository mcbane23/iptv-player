package com.bane23.iptvplayer.ui.mobile

import android.os.PowerManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.bane23.iptvplayer.player.background.BackgroundPlayback
import com.bane23.iptvplayer.ui.common.SettingsViewModel

/**
 * Šta plejer na telefonu radi kad aplikacija ode sa ekrana i kad se vrati. Bez opcije „Puštanje u
 * pozadini” strim se zaustavlja čim aplikacija nije vidljiva. Sa njom gledanje koje je u toku
 * nastavlja kad se ekran ugasi ili aplikacija ode u pozadinu; pauziran sadržaj se zaustavlja kao i
 * do sada. Mali prozor (slika u slici) koji korisnik zatvori uvek zaustavlja puštanje.
 */
@Composable
internal fun PlayerLifecycle(
    player: ExoPlayer,
    title: String,
    inPip: Boolean,
    onTogglePlayPause: () -> Unit,
    onForeground: () -> Unit,
    onBackground: (keepPlaying: Boolean) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val enabled by settingsViewModel.backgroundPlay.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current.applicationContext
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val currentTitle by rememberUpdatedState(title)
    val toggle by rememberUpdatedState(onTogglePlayPause)
    val foreground by rememberUpdatedState(onForeground)
    val background by rememberUpdatedState(onBackground)
    // Da li je plejer bio u malom prozoru otkad je poslednji put bio preko celog ekrana.
    val pipSinceResume = remember { booleanArrayOf(false) }
    if (inPip) pipSinceResume[0] = true

    LifecycleEventEffect(Lifecycle.Event.ON_START) { foreground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pipSinceResume[0] = false
        BackgroundPlayback.stop()
    }
    // Servis se pokreće dok je aplikacija još na ekranu; kasnije sistem to više ne dozvoljava.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (activity?.isInPictureInPictureMode == true) pipSinceResume[0] = true
        if (enabled && activity?.isFinishing != true) {
            BackgroundPlayback.start(
                context,
                BackgroundPlayback.Target(
                    player = player,
                    title = currentTitle,
                    toggle = { toggle() },
                    stop = { background(false) },
                ),
            )
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Mali prozor nestaje sa ekrana kad ga korisnik zatvori ili kad se ekran ugasi; samo ovo drugo nastavlja.
        val closedPip = pipSinceResume[0] && power?.isInteractive != false
        val keep = BackgroundPlayback.active && player.playWhenReady && !closedPip
        if (!keep) BackgroundPlayback.stop()
        background(keep)
    }
    DisposableEffect(Unit) {
        onDispose { BackgroundPlayback.stop() }
    }
}
