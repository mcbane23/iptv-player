package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Pozadina aplikacije u izgledu „Staklo”: tamni preliv boje [background] (svetlije gore levo, tamnije
 * dole desno), meko svetlo akcentne boje [accent] u gornjem levom uglu, iza menija, i slabije hladno
 * svetlo dole desno. Prelivi se prave jednom za datu veličinu (drawWithCache), pa ne opterećuju
 * slabije uređaje. U crnoj temi nema ni preliva ni svetla: tamo je poenta da pozadina bude potpuno crna.
 */
fun Modifier.glowBackground(accent: Color, background: Color): Modifier = drawWithCache {
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
    onDrawBehind {
        if (background == BlackBackground) {
            drawRect(background)
        } else {
            drawRect(base)
            drawRect(glow)
            drawRect(cool)
        }
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
