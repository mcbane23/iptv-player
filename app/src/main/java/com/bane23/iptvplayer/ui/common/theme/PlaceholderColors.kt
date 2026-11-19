package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Boje mesta koja stoje dok sadržaj ne stigne: podloga postera i slike epizode, prelaz iza naziva
 * kad postera nema, obrisi pri učitavanju i podloga inicijala kanala bez logoa. Prate temu, da u
 * Teget temi ne ostanu sive mrlje. Siva (i crna) tema ima iste boje kao pre.
 */
@Immutable
data class PlaceholderColors(
    val image: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val skeleton: Color,
    val logo: Color,
)

val GrayPlaceholders = PlaceholderColors(
    image = Color(0xFF22262E),
    gradientTop = Color(0xFF2C323C),
    gradientBottom = Color(0xFF181C22),
    skeleton = Color(0xFF232930),
    logo = SurfaceVariant,
)

val NavyPlaceholders = PlaceholderColors(
    image = Color(0xFF16264F),
    gradientTop = Color(0xFF22366E),
    gradientBottom = Color(0xFF0F1B3E),
    skeleton = Color(0xFF1A2A58),
    logo = NavySurfaceVariant,
)

val LocalPlaceholderColors = staticCompositionLocalOf { GrayPlaceholders }
