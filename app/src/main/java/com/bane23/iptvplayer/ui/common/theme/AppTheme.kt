package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R

/**
 * Tema aplikacije koju korisnik bira u Podešavanjima: boje pozadine i površina i da li izabrana
 * dugme u meniju dobija blago neonsko svetlo iza sebe. U podešavanjima se čuva ime vrednosti, zato se postojeća imena ne menjaju.
 * Crna tema je poseban prekidač i preko bilo koje od ovih stavlja potpuno crnu pozadinu.
 */
enum class AppTheme(val background: Color, val surface: Color, val surfaceVariant: Color, val neon: Boolean) {
    GRAY(Background, Surface, SurfaceVariant, neon = false),
    NAVY(NavyBackground, NavySurface, NavySurfaceVariant, neon = false),
    NAVY_NEON(NavyBackground, NavySurface, NavySurfaceVariant, neon = true),
    GRAY_NEON(Background, Surface, SurfaceVariant, neon = true),
    ;

    companion object {
        val DEFAULT = NAVY_NEON

        /** Tema po sačuvanom imenu; nepoznato ime ili null daje podrazumevanu. */
        fun fromName(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

@Composable
fun AppTheme.label(): String = stringResource(
    when (this) {
        AppTheme.GRAY -> R.string.theme_gray
        AppTheme.NAVY -> R.string.theme_navy
        AppTheme.NAVY_NEON -> R.string.theme_navy_neon
        AppTheme.GRAY_NEON -> R.string.theme_gray_neon
    },
)

/** Boje neonske teme: [glow] je svetlo iza izabranog dugmeta u meniju, [progress] boja traka napretka. */
data class NeonStyle(val glow: Color, val progress: Color)

/** Neonski stil trenutne teme, ili null kad tema nije neonska. */
val LocalNeon = staticCompositionLocalOf<NeonStyle?> { null }

/**
 * Blago neonsko svetlo iza izabranog dugmeta u meniju (donja traka na telefonu, bočni meni na TV-u)
 * kad je [active] i tema je neonska ([neon] nije null): mek kružni prelaz akcentne boje iz sredine
 * dugmeta, bez okvira. Crta se jednom po veličini (drawWithCache), pa ne opterećuje slabije uređaje.
 */
fun Modifier.neonBackdrop(neon: NeonStyle?, active: Boolean): Modifier =
    if (neon == null || !active) {
        this
    } else {
        drawWithCache {
            val radius = size.maxDimension * BACKDROP_RADIUS
            val mid = size.center
            val brush = Brush.radialGradient(
                colors = listOf(neon.glow.copy(alpha = BACKDROP_ALPHA), Color.Transparent),
                center = mid,
                radius = radius,
            )
            onDrawBehind { drawCircle(brush, radius = radius, center = mid) }
        }
    }

private const val BACKDROP_ALPHA = 0.28f
private const val BACKDROP_RADIUS = 0.75f
