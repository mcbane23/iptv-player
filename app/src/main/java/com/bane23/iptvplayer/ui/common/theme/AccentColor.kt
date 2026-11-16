package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R

/**
 * Boje koje korisnik može da izabere kao akcentnu boju aplikacije (Podešavanja). Sve su svetle,
 * jer stoje na tamnoj temi, pa uz svaku ide isti tamni tekst ([OnAccent]). U podešavanjima se čuva
 * ime vrednosti, zato se postojeća imena ne menjaju.
 */
enum class AccentColor(val color: Color) {
    ORANGE(Color(0xFFFFB74D)),
    BLUE(Color(0xFF4FC3F7)),
    GREEN(Color(0xFF81C784)),
    TEAL(Color(0xFF4DB6AC)),
    PURPLE(Color(0xFFB39DDB)),
    PINK(Color(0xFFF48FB1)),
    ;

    companion object {
        val DEFAULT = ORANGE

        /** Boja po sačuvanom imenu; nepoznato ime ili null daje podrazumevanu. */
        fun fromName(name: String?): AccentColor = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

@Composable
fun AccentColor.label(): String = stringResource(
    when (this) {
        AccentColor.ORANGE -> R.string.accent_orange
        AccentColor.BLUE -> R.string.accent_blue
        AccentColor.GREEN -> R.string.accent_green
        AccentColor.TEAL -> R.string.accent_teal
        AccentColor.PURPLE -> R.string.accent_purple
        AccentColor.PINK -> R.string.accent_pink
    },
)
