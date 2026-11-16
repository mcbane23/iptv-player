package com.bane23.iptvplayer.ui.common

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bane23.iptvplayer.R
import kotlinx.coroutines.delay

private const val EXIT_WINDOW_MS = 2_000L

/**
 * Izlaz iz aplikacije tek na drugi pritisak „Nazad”: prvi samo prikaže poruku, a drugi u roku od
 * dve sekunde prolazi dalje i zatvara aplikaciju. Stavlja se na početni ekran; [enabled] je false
 * dok „Nazad” na tom ekranu radi nešto drugo (npr. vraća na karticu Uživo).
 */
@Composable
fun ExitOnDoubleBack(enabled: Boolean = true) {
    val context = LocalContext.current
    var armed by remember { mutableStateOf(false) }
    BackHandler(enabled = enabled && !armed) {
        armed = true
        Toast.makeText(context, R.string.exit_press_again, Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(armed) {
        if (armed) {
            delay(EXIT_WINDOW_MS)
            armed = false
        }
    }
}
