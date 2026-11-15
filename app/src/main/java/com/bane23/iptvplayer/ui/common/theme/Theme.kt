package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
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

// Naslovi su nešto deblji od podrazumevanih, da se sekcije i nazivi jasno izdvoje od ostalog teksta.
private val MobileType = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val TvType = TvTypography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tamna tema aplikacije. Postavlja i Material3 (telefon) i TV Material temu. */
@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors, typography = MobileType) {
        TvMaterialTheme(colorScheme = TvColors, typography = TvType, content = content)
    }
}
