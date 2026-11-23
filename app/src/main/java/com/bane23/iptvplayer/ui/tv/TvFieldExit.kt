package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Strelice daljinskog uvek izvode fokus iz polja za kucanje na TV-u. Compose to sam radi samo za
 * tastere koje sistem prijavi kao pravi D-pad; kod daljinskih koji se javljaju kao tastatura (i kod
 * tastera poslatih preko adb-a) strelice bi samo pomerale kursor, pa fokus ne bi mogao da izađe iz
 * polja posle zatvaranja tastature. Kad u tom smeru nema ničega, taster ide polju kao i inače.
 */
@Composable
fun Modifier.tvFieldExit(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        val direction = when (event.nativeKeyEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
            else -> return@onPreviewKeyEvent false
        }
        event.type == KeyEventType.KeyDown && focusManager.moveFocus(direction)
    }
}
