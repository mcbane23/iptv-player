package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R

/**
 * Tema aplikacije koju korisnik bira u Podešavanjima: boje pozadine i površina i da li izabrana
 * stavka dobija neonski sjaj. U podešavanjima se čuva ime vrednosti, zato se postojeća imena ne menjaju.
 * Crna tema je poseban prekidač i preko bilo koje od ovih stavlja potpuno crnu pozadinu.
 */
enum class AppTheme(val background: Color, val surface: Color, val surfaceVariant: Color, val neon: Boolean) {
    GRAY(Background, Surface, SurfaceVariant, neon = false),
    NAVY(NavyBackground, NavySurface, NavySurfaceVariant, neon = false),
    NAVY_NEON(NavyBackground, NavySurface, NavySurfaceVariant, neon = true),
    GRAY_NEON(Background, Surface, SurfaceVariant, neon = true),
    ;

    companion object {
        val DEFAULT = GRAY

        /** Tema po sačuvanom imenu; nepoznato ime ili null daje podrazumevanu. */
        fun fromName(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: DEFAULT

        /** Sledeća tema u krug (svaki pritisak u podešavanjima prelazi na sledeću). */
        fun next(current: AppTheme): AppTheme = entries[(current.ordinal + 1) % entries.size]
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

/** Boje neonske teme: [glow] je sjaj oko izabrane stavke, [progress] boja traka napretka. */
data class NeonStyle(val glow: Color, val progress: Color)

/** Neonski stil trenutne teme, ili null kad tema nije neonska. */
val LocalNeon = staticCompositionLocalOf<NeonStyle?> { null }

/**
 * Neonski sjaj oko oblika [shape] kad je [active] i tema je neonska ([neon] nije null): nekoliko sve
 * širih i providnijih linija po ivici, bez zamućenja, pa je jeftino i na slabijim TV kutijama.
 * Stavlja se pre `clip` u nizu, da sjaj sme da izađe van stavke.
 */
fun Modifier.neonGlow(neon: NeonStyle?, active: Boolean, shape: Shape): Modifier =
    if (neon == null || !active) {
        this
    } else {
        drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            for ((width, alpha) in GLOW_LAYERS) {
                drawOutline(outline, neon.glow.copy(alpha = alpha), style = Stroke(width.dp.toPx()))
            }
        }
    }

/** Širina linije (dp) i providnost, od najšireg i najbleđeg sloja ka ivici. */
private val GLOW_LAYERS = listOf(16f to 0.06f, 11f to 0.11f, 7f to 0.2f, 4f to 0.38f)
