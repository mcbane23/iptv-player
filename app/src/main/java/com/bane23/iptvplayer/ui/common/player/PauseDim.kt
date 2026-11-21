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

/**
 * Posle ovoliko pauze kontrole ustupe mesto ekranu pauze (naslov, opis, sat). Kad je ekran pauze
 * isključen u Podešavanjima, slika se posle istog vremena samo zatamni.
 */
const val PAUSE_INFO_AFTER_MS = 10_000L

/** Posle ovoliko pauze plejer se zatvara. */
const val PAUSE_EXIT_AFTER_MS = 30 * 60_000L

/** Koliko slike ostaje vidljivo iza ekrana pauze. */
private const val INFO_PICTURE = 0.4f
private const val INFO_FADE_MS = 300

/**
 * Pauza: posle kratkog čekanja kontrole ustupe mesto ekranu pauze, koji ostaje dok se nešto ne
 * pritisne. Prvi taster ili dodir samo vraća kontrole, ne radi ništa drugo. Zaboravljena pauza
 * zatvara plejer, da nepomična slika ne ostane na ekranu.
 */
@Stable
class PauseDimState {
    /** Ekran pauze je preko slike umesto kontrola. */
    var info by mutableStateOf(false)
        private set

    /** Ekran pauze je isključen u Podešavanjima: slika je samo zatamnjena, bez teksta. */
    var plain by mutableStateOf(false)
        private set

    /** Raste sa svakim tasterom ili dodirom; odbrojavanje tada kreće iznova. */
    var activity by mutableIntStateOf(0)
        private set

    /** Taster koji je vratio sliku; guta se dok se ne pusti, da ne bi još nešto uradio. */
    private var wakeKey: Int? = null

    /** Preko slike je ekran pauze, pa prvi taster ili dodir samo vraća kontrole. */
    val covered: Boolean get() = info

    fun touch() {
        activity++
        info = false
    }

    internal fun showInfo(plain: Boolean) {
        this.plain = plain
        info = true
    }

    internal fun clear() {
        info = false
    }

    /** Vraća true kad je taster potrošen na vraćanje slike. */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        when (event.type) {
            KeyEventType.KeyDown -> {
                // Dugme za puštanje odmah nastavlja, bez dodatnog pritiska.
                if (covered && !isPlayKey(code)) wakeKey = code
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
 * Odbrojava dok je sadržaj pauziran: posle [PAUSE_EXIT_AFTER_MS] zove [onExit]. Dok je [enabled]
 * false (otvoren je izbor u zasebnom prozoru) ne radi ništa. Sa [info] se posle
 * [PAUSE_INFO_AFTER_MS] prikazuje ekran pauze i ostaje do tastera, dodira ili izlaza. Kad je
 * [describe] false (isključeno u Podešavanjima), slika se posle istog vremena samo zatamni.
 */
@Composable
fun rememberPauseDim(
    status: PlaybackStatus,
    enabled: Boolean = true,
    info: Boolean = false,
    describe: Boolean = true,
    onExit: () -> Unit,
): PauseDimState {
    val state = remember { PauseDimState() }
    val exit by rememberUpdatedState(onExit)
    val paused = enabled && status == PlaybackStatus.PAUSED
    LaunchedEffect(paused, info, describe, state.activity) {
        if (!paused) {
            state.clear()
            return@LaunchedEffect
        }
        if (info) {
            delay(PAUSE_INFO_AFTER_MS)
            state.showInfo(plain = !describe)
            delay(PAUSE_EXIT_AFTER_MS - PAUSE_INFO_AFTER_MS)
        } else {
            state.clear()
            delay(PAUSE_EXIT_AFTER_MS)
        }
        exit()
    }
    return state
}

/** Beleži tastere i dodire; dok je preko slike ekran pauze, prvi od njih samo vraća kontrole. */
fun Modifier.pauseDimInput(state: PauseDimState): Modifier = this
    .onPreviewKeyEvent(state::onKey)
    .pointerInput(state) {
        awaitPointerEventScope {
            var swallow = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.changedToDown() }) {
                    if (state.covered) swallow = true
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
 * Sve što stoji preko slike (kontrole, paneli, poruke). Dok je preko slike ekran pauze ([info]),
 * sadržaj se ne vidi, ali ostaje na mestu, da fokus ne bi nestao.
 */
@Composable
fun PauseDimLayer(
    state: PauseDimState,
    info: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val infoShown by animateFloatAsState(
        targetValue = if (state.info) 1f else 0f,
        animationSpec = tween(INFO_FADE_MS),
        label = "pauseInfo",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val shade = infoShown * (1f - INFO_PICTURE)
                if (shade > 0f) drawRect(Color.Black.copy(alpha = shade))
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - infoShown },
            content = content,
        )
        if (info != null && !state.plain && infoShown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = infoShown },
                content = info,
            )
        }
    }
}
