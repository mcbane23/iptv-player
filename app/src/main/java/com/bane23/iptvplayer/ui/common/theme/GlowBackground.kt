package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Tamna pozadina aplikacije sa blagim sjajem akcentne boje [accent] u gornjem levom uglu, iza menija.
 * Prelaz se pravi jednom za datu veličinu (drawWithCache), pa ne opterećuje slabije uređaje.
 */
fun Modifier.glowBackground(accent: Color): Modifier = drawWithCache {
    val glow = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = GLOW_ALPHA), Color.Transparent),
        center = Offset.Zero,
        radius = size.maxDimension * GLOW_RADIUS,
    )
    onDrawBehind {
        drawRect(Background)
        drawRect(glow)
    }
}

private const val GLOW_ALPHA = 0.13f
private const val GLOW_RADIUS = 0.6f
