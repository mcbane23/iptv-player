package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.cast.IncomingTransfer
import com.bane23.iptvplayer.data.cast.CastPairing
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.cast.CastPlayerViewModel
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.statusMessage
import kotlinx.coroutines.delay
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass

/**
 * Plejer za sadržaj poslat sa telefona. Njime se upravlja sa telefona; na daljinskom TV-a OK
 * pauzira i nastavlja, a Nazad zatvara. Zatvara se i kad telefon zaustavi puštanje.
 */
@Composable
fun TvCastPlayerScreen(onClose: () -> Unit, viewModel: CastPlayerViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(session == null) {
        if (session == null) onClose()
    }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        if (event.type == KeyEventType.KeyUp) viewModel.togglePlayPause()
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        VideoSurface(player = viewModel.player, aspectMode = AspectMode.FIT, modifier = Modifier.fillMaxSize())

        statusMessage(sourceMissing = false, playback = playback)?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp),
            )
        }

        // Naslov se vidi dok se ne gleda (učitavanje, pauza), da slika ostane čista.
        if (playback.status != PlaybackStatus.PLAYING) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 48.dp, vertical = 27.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = session?.request?.title.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.cast_from_phone),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Kod za prvo povezivanje telefona, preko bilo kog ekrana. Sklanja se sam kad kod istekne. */
@Composable
fun TvCastPairingCode(code: String?, onExpired: () -> Unit, modifier: Modifier = Modifier) {
    if (code == null) return
    LaunchedEffect(code) {
        delay(CastPairing.VALID_MS)
        onExpired()
    }
    Column(
        modifier = modifier
            .padding(horizontal = 48.dp, vertical = 27.dp)
            .glass(Glass.CardShape, strong = true)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.cast_tv_code_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = code.toList().joinToString("  "),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Obaveštenje preko bilo kog ekrana dok stižu podešavanja sa telefona; ishod se sklanja sam. */
@Composable
fun TvIncomingTransfer(transfer: IncomingTransfer?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (transfer == null) return
    val result = transfer.result
    LaunchedEffect(result) {
        if (result != null) {
            delay(TRANSFER_RESULT_MS)
            onDone()
        }
    }
    Column(
        modifier = modifier
            .padding(horizontal = 48.dp, vertical = 27.dp)
            .glass(Glass.CardShape, strong = true)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.transfer_tv_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = when {
                result != null -> stringResource(R.string.transfer_summary, result.added, result.existing, result.failed)
                transfer.total > 0 ->
                    stringResource(R.string.transfer_tv_working, transfer.step.coerceAtLeast(1), transfer.total)
                else -> stringResource(R.string.transfer_tv_applying)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private const val TRANSFER_RESULT_MS = 8_000L
