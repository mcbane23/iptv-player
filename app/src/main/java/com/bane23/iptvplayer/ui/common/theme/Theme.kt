package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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

// Oblici Material delova u izgledu „Staklo”: dugmad za izbor su kapsule, kartice i meniji jače zaobljeni.
private val MobileShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = Glass.PillShape,
    medium = Glass.CardShape,
    large = RoundedCornerShape(22.dp),
)

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
 * ekrane i mračnu sobu), a površine su za nijansu tamnije.
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
    // Dijalozi, meniji, kartice i dugmad za izbor iz Material biblioteke prate temu i boju aplikacije
    // (podrazumevane su sivo-ljubičaste), da se nijedan deo ne izdvaja iz izgleda „Staklo”.
    val mobileColors = remember(accent, theme, black) {
        val tint = accent.color.copy(alpha = Glass.SELECTED_FILL).compositeOver(surface)
        MobileColors.copy(
            primary = accent.color,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            surfaceContainerLowest = background,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = lerp(surface, surfaceVariant, 0.5f),
            surfaceContainerHighest = surfaceVariant,
            surfaceTint = Color.Transparent,
            secondary = accent.color,
            onSecondary = OnAccent,
            secondaryContainer = tint,
            onSecondaryContainer = accent.color,
            primaryContainer = tint,
            onPrimaryContainer = accent.color,
            tertiary = accent.color,
            onTertiary = OnAccent,
            outline = Color.White.copy(alpha = 0.26f).compositeOver(background),
            outlineVariant = Color.White.copy(alpha = 0.12f).compositeOver(background),
        )
    }
    val tvColors = remember(accent, theme, black) {
        TvColors.copy(primary = accent.color, background = background, surface = surface, surfaceVariant = surfaceVariant)
    }
    // Crna tema zadržava sive obrise, kao i pre.
    val placeholders = if (black) GrayPlaceholders else theme.placeholders
    MaterialTheme(colorScheme = mobileColors, typography = MobileType, shapes = MobileShapes) {
        TvMaterialTheme(colorScheme = tvColors, typography = TvType) {
            CompositionLocalProvider(LocalPlaceholderColors provides placeholders, content = content)
        }
    }
}
