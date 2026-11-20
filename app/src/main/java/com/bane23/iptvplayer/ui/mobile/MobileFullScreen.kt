package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Da li je cela aplikacija na telefonu preko celog ekrana (izbor u podešavanjima). */
internal val LocalAppFullScreen = staticCompositionLocalOf { false }

/**
 * Po izboru u podešavanjima sakriva gornju i donju sistemsku traku na svim ekranima telefona.
 * Prevlačenjem od ivice trake se privremeno pojave i same se ponovo sakriju.
 * Bez gornje trake sadržaj se penje bliže vrhu: počinje malo ispod donje ivice kamere, umesto
 * ispod cele visine mesta za kameru, pa kamera ne preklapa gornji red.
 * Kamera sa strane (položen telefon) i dalje zavisi od izbora „Cela širina ekrana”.
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
    // Troši se samo deo umetka za kameru ispod njene donje ivice; ostatak ekrani poštuju kao razmak.
    // Umetak se čita u kompoziciji, pa se posle okretanja telefona ovo ponovo računa.
    val density = LocalDensity.current
    val cutoutTop = WindowInsets.displayCutout.only(WindowInsetsSides.Top).getTop(density)
    val cameraBottom = ViewCompat.getRootWindowInsets(view)?.displayCutout
        ?.boundingRects?.filter { it.top < cutoutTop }?.maxOfOrNull { it.bottom } ?: cutoutTop
    val keep = (cameraBottom + with(density) { CAMERA_MARGIN.roundToPx() }).coerceAtMost(cutoutTop)
    val released = WindowInsets(top = (cutoutTop - keep).coerceAtLeast(0))
    CompositionLocalProvider(LocalAppFullScreen provides enabled) {
        Box(
            modifier = if (enabled) {
                Modifier.consumeWindowInsets(released)
            } else {
                Modifier
            },
        ) { content() }
    }
}

/** Razmak između donje ivice kamere i sadržaja kad je ceo ekran uključen. */
private val CAMERA_MARGIN = 6.dp
