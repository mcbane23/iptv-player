package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R

/**
 * Tema aplikacije koju korisnik bira u Podešavanjima: boje pozadine i površina. U podešavanjima se
 * čuva ime vrednosti, zato se postojeća imena ne menjaju. Crna tema je poseban prekidač i preko bilo
 * koje od ovih stavlja potpuno crnu pozadinu.
 */
enum class AppTheme(val background: Color, val surface: Color, val surfaceVariant: Color) {
    GRAY(Background, Surface, SurfaceVariant),
    NAVY(NavyBackground, NavySurface, NavySurfaceVariant),
    ;

    companion object {
        val DEFAULT = GRAY

        /**
         * Tema po sačuvanom imenu; nepoznato ime ili null daje podrazumevanu. Neonske teme iz verzije
         * 1.0.11 su uklonjene (Bane), pa se njihova sačuvana imena čitaju kao ista boja bez neona.
         */
        fun fromName(name: String?): AppTheme = when (name) {
            "NAVY_NEON" -> NAVY
            "GRAY_NEON" -> GRAY
            else -> entries.firstOrNull { it.name == name } ?: DEFAULT
        }
    }
}

@Composable
fun AppTheme.label(): String = stringResource(
    when (this) {
        AppTheme.GRAY -> R.string.theme_gray
        AppTheme.NAVY -> R.string.theme_navy
    },
)
