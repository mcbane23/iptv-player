package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Da li je cela aplikacija na telefonu preko celog ekrana (izbor u podešavanjima). */
internal val LocalAppFullScreen = staticCompositionLocalOf { false }

/**
 * Po izboru u podešavanjima sakriva gornju i donju sistemsku traku na svim ekranima telefona.
 * Prevlačenjem od ivice trake se privremeno pojave i same se ponovo sakriju.
 */
@Composable
fun AppFullScreen(enabled: Boolean, content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view, enabled) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    CompositionLocalProvider(LocalAppFullScreen provides enabled, content = content)
}
