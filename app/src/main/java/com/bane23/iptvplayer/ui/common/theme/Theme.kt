package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

private val MobileColors = darkColorScheme(
    primary = AccentColor.DEFAULT.color,
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
    primary = AccentColor.DEFAULT.color,
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

/**
 * Tamna tema aplikacije sa akcentnom bojom [accent] koju bira korisnik i bojama pozadine iz [theme].
 * Postavlja i Material3 (telefon) i TV Material temu. Sa [black] je pozadina potpuno crna (za OLED
 * ekrane i mračnu sobu), a površine su za nijansu tamnije. Neonska tema daje [LocalNeon].
 */
@Composable
fun IptvTheme(
    accent: AccentColor = AccentColor.DEFAULT,
    theme: AppTheme = AppTheme.DEFAULT,
    black: Boolean = false,
    content: @Composable () -> Unit,
) {
    val background = if (black) BlackBackground else theme.background
    val surface = if (black) BlackSurface else theme.surface
    val surfaceVariant = if (black) BlackSurfaceVariant else theme.surfaceVariant
    val mobileColors = remember(accent, theme, black) {
        MobileColors.copy(primary = accent.color, background = background, surface = surface, surfaceVariant = surfaceVariant)
    }
    val tvColors = remember(accent, theme, black) {
        TvColors.copy(primary = accent.color, background = background, surface = surface, surfaceVariant = surfaceVariant)
    }
    val neon = remember(accent, theme) { if (theme.neon) NeonStyle(glow = accent.color, progress = IceBlue) else null }
    MaterialTheme(colorScheme = mobileColors, typography = MobileType) {
        TvMaterialTheme(colorScheme = tvColors, typography = TvType) {
            CompositionLocalProvider(LocalNeon provides neon, content = content)
        }
    }
}
