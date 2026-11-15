package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

private val MobileColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
)

private val TvColors = tvDarkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
)

/** Tamna tema aplikacije. Postavlja i Material3 (telefon) i TV Material temu. */
@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors) {
        TvMaterialTheme(colorScheme = TvColors, content = content)
    }
}
