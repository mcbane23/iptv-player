package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Pozadina aplikacije u izgledu „Staklo”: tamni preliv boje [background] (svetlije gore levo, tamnije
 * dole desno), meko svetlo akcentne boje [accent] u gornjem levom uglu, iza menija, i slabije hladno
 * svetlo dole desno. U crnoj temi nema ni preliva ni svetla: tamo je poenta da pozadina bude potpuno crna.
 *
 * Tri preliva preko celog ekrana u svakom kadru bila su preskupa za grafiku slabijih TV uređaja
 * (listanje na projektoru se osetno usporilo). Zato se prelivi nacrtaju jednom u malu sliku
 * ([GlowCache]), a u svakom kadru se crta samo ta slika, razvučena preko ekrana: jedan neproziran
 * pravougaonik. Prelivi su meki, pa se razvlačenje ne vidi.
 */
fun Modifier.glowBackground(accent: Color, background: Color): Modifier = composed {
    val cache = remember { GlowCache() }
    drawBehind {
        if (background == BlackBackground || size.isEmpty()) {
            drawRect(background)
        } else {
            drawImage(
                image = cache.image(this, accent, background),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** Pozadina nacrtana u maloj slici; pravi se ponovo samo kad se promeni boja ili veličina ekrana. */
private class GlowCache {
    private var bitmap: ImageBitmap? = null
    private var accent = Color.Unspecified
    private var background = Color.Unspecified
    private var width = 0
    private var height = 0

    fun image(scope: DrawScope, accent: Color, background: Color): ImageBitmap {
        val w = (scope.size.width / DOWNSCALE).roundToInt().coerceAtLeast(1)
        val h = (scope.size.height / DOWNSCALE).roundToInt().coerceAtLeast(1)
        bitmap?.let {
            if (accent == this.accent && background == this.background && w == width && h == height) return it
        }
        val made = ImageBitmap(w, h)
        val size = Size(w.toFloat(), h.toFloat())
        val base = Brush.linearGradient(
            colors = listOf(lerp(background, Color.White, BASE_LIGHTEN), lerp(background, Color.Black, BASE_DARKEN)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val glow = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = GLOW_ALPHA), Color.Transparent),
            center = Offset.Zero,
            radius = size.maxDimension * GLOW_RADIUS,
        )
        val cool = Brush.radialGradient(
            colors = listOf(CoolLight.copy(alpha = COOL_ALPHA), Color.Transparent),
            center = Offset(size.width, size.height),
            radius = size.maxDimension * COOL_RADIUS,
        )
        CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(made), size) {
            drawRect(base)
            drawRect(glow)
            drawRect(cool)
        }
        bitmap = made
        this.accent = accent
        this.background = background
        width = w
        height = h
        return made
    }
}

/** Pozadina aplikacije sa bojama trenutne teme; za ekrane koji je crtaju sami. */
fun Modifier.appBackground(): Modifier = composed {
    glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
}

private val CoolLight = Color(0xFF3D6FD0)
private const val BASE_LIGHTEN = 0.05f
private const val BASE_DARKEN = 0.35f
private const val GLOW_ALPHA = 0.17f
private const val GLOW_RADIUS = 0.5f
private const val COOL_ALPHA = 0.10f
private const val COOL_RADIUS = 0.55f

/** Slika pozadine je ovoliko puta manja od ekrana po svakoj strani. */
private const val DOWNSCALE = 4f
