package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * kad je [active] i tema je neonska ([neon] nije null). Stavlja se na samu ikonicu (ili njenu
 * podlogu): iz njene sredine se širi jedva vidljiv eliptičan sjaj akcentne boje, širok [width] i visok
 * [height], bez oštre ivice (Bane: upola slabije od test verzije #141). Crta se van granica elementa, pa modifikator mora
 * da stoji pre eventualnog clip-a. Prelaz se pravi jednom po veličini (drawWithCache).
 */
fun Modifier.neonBackdrop(neon: NeonStyle?, active: Boolean, width: Dp, height: Dp): Modifier =
    if (neon == null || !active) {
        this
    } else {
        drawWithCache {
            val rx = width.toPx() / 2f
            val ry = height.toPx() / 2f
            val mid = size.center
            val brush = Brush.radialGradient(
                0f to neon.glow.copy(alpha = BACKDROP_CORE_ALPHA),
                0.35f to neon.glow.copy(alpha = BACKDROP_MID_ALPHA),
                1f to Color.Transparent,
                center = mid,
                radius = rx,
            )
            onDrawBehind {
                // Krug poluprečnika rx, spljošten po visini u elipsu rx × ry.
                scale(scaleX = 1f, scaleY = ry / rx, pivot = mid) {
                    drawCircle(brush, radius = rx, center = mid)
                }
            }
        }
    }

/**
 * Blago neonsko svetlo oko izabranog čipa kad je [active] i tema je neonska: nekoliko providnih
 * zaobljenih slojeva akcentne boje koji se šire od ivice čipa i bledi ka spolja, bez okvira.
 * [corner] je zaobljenje samog čipa (null = poluvisina, za čip u obliku pilule). [height] je
 * visina vidljivog čipa kad je element viši od njega (Material čip dobija 48 dp za dodir), pa se
 * svetlo centrira po visini. Stoji pre clip-a.
 */
fun Modifier.neonHalo(neon: NeonStyle?, active: Boolean, corner: Dp? = null, height: Dp? = null): Modifier =
    if (neon == null || !active) {
        this
    } else {
        drawWithCache {
            val step = HALO_STEP.toPx()
            val color = neon.glow.copy(alpha = HALO_LAYER_ALPHA)
            val chipHeight = height?.toPx()?.coerceAtMost(size.height) ?: size.height
            val top = (size.height - chipHeight) / 2f
            val baseCorner = corner?.toPx() ?: (chipHeight / 2f)
            onDrawBehind {
                for (i in HALO_LAYERS downTo 1) {
                    val grow = step * i
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(-grow, top - grow),
                        size = Size(size.width + grow * 2, chipHeight + grow * 2),
                        cornerRadius = CornerRadius(baseCorner + grow),
                    )
                }
            }
        }
    }

private const val HALO_LAYERS = 6
private val HALO_STEP = 2.5.dp
private const val HALO_LAYER_ALPHA = 0.022f

private const val BACKDROP_CORE_ALPHA = 0.14f
private const val BACKDROP_MID_ALPHA = 0.05f
