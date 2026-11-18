package com.bane23.iptvplayer.ui.common.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import com.bane23.iptvplayer.player.PlaybackStatus
import kotlinx.coroutines.delay

/** Posle ovoliko pauze slika se zatamni, a kontrole se sklone. */
const val PAUSE_DIM_AFTER_MS = 60_000L

/** Posle ovoliko pauze plejer se zatvara. */
const val PAUSE_EXIT_AFTER_MS = 30 * 60_000L

/** Koliko slike ostaje vidljivo dok je zatamnjena. */
private const val DIMMED_PICTURE = 0.3f
private const val DIM_FADE_MS = 600

/**
 * Zaštita ekrana od urezivanja slike: zaboravljena pauza ne ostavlja nepomičnu sliku i kontrole
 * punim sjajem. Prvi taster ili dodir samo vraća sliku, ne radi ništa drugo.
 */
@Stable
class PauseDimState {
    var dimmed by mutableStateOf(false)
        private set

    /** Raste sa svakim tasterom ili dodirom; odbrojavanje tada kreće iznova. */
    var activity by mutableIntStateOf(0)
        private set

    /** Taster koji je vratio sliku; guta se dok se ne pusti, da ne bi još nešto uradio. */
    private var wakeKey: Int? = null

    fun touch() {
        activity++
        dimmed = false
    }

    internal fun dim() {
        dimmed = true
    }

    internal fun clear() {
        dimmed = false
    }

    /** Vraća true kad je taster potrošen na vraćanje slike. */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        when (event.type) {
            KeyEventType.KeyDown -> {
                // Dugme za puštanje odmah nastavlja, bez dodatnog pritiska.
                if (dimmed && !isPlayKey(code)) wakeKey = code
                touch()
            }

            KeyEventType.KeyUp -> if (wakeKey == code) {
                wakeKey = null
                return true
            }
        }
        return wakeKey == code
    }

    private fun isPlayKey(code: Int) = code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
        code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY ||
        code == AndroidKeyEvent.KEYCODE_MEDIA_PAUSE
}

/**
 * Odbrojava dok je sadržaj pauziran: posle [PAUSE_DIM_AFTER_MS] zatamni sliku, posle
 * [PAUSE_EXIT_AFTER_MS] zove [onExit]. Dok je [enabled] false (otvoren je izbor u zasebnom prozoru)
 * ne radi ništa.
 */
@Composable
fun rememberPauseDim(status: PlaybackStatus, enabled: Boolean = true, onExit: () -> Unit): PauseDimState {
    val state = remember { PauseDimState() }
    val exit by rememberUpdatedState(onExit)
    val paused = enabled && status == PlaybackStatus.PAUSED
    LaunchedEffect(paused, state.activity) {
        if (!paused) {
            state.clear()
            return@LaunchedEffect
        }
        delay(PAUSE_DIM_AFTER_MS)
        state.dim()
        delay(PAUSE_EXIT_AFTER_MS - PAUSE_DIM_AFTER_MS)
        exit()
    }
    return state
}

/** Beleži tastere i dodire; dok je slika zatamnjena, prvi od njih samo vraća sliku. */
fun Modifier.pauseDimInput(state: PauseDimState): Modifier = this
    .onPreviewKeyEvent(state::onKey)
    .pointerInput(state) {
        awaitPointerEventScope {
            var swallow = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.changedToDown() }) {
                    if (state.dimmed) swallow = true
                    state.touch()
                }
                if (swallow) {
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) swallow = false
                }
            }
        }
    }

/**
 * Sve što stoji preko slike (kontrole, paneli, poruke). Dok je slika zatamnjena, sadržaj se ne vidi,
 * ali ostaje na mestu, da fokus ne bi nestao.
 */
@Composable
fun PauseDimLayer(state: PauseDimState, content: @Composable BoxScope.() -> Unit) {
    val dim by animateFloatAsState(
        targetValue = if (state.dimmed) 1f else 0f,
        animationSpec = tween(DIM_FADE_MS),
        label = "pauseDim",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { if (dim > 0f) drawRect(Color.Black.copy(alpha = dim * (1f - DIMMED_PICTURE))) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - dim },
            content = content,
        )
    }
}
