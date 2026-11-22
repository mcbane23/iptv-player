package com.bane23.iptvplayer.ui.common

import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import com.bane23.iptvplayer.R

/**
 * Položaj ekrana cele aplikacije na telefonu. U podešavanjima se čuva ime vrednosti, zato se
 * postojeća imena ne menjaju. [request] je vrednost koju aktivnost traži od sistema; „uvek
 * uspravno” i „uvek položeno” važe i kad je rotacija na telefonu zaključana.
 */
enum class ScreenPosition(@StringRes val label: Int, val request: Int) {
    AUTO(R.string.screen_position_auto, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT(R.string.screen_position_portrait, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE(R.string.screen_position_landscape, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    ;

    companion object {
        /** Položaj po sačuvanom imenu; nepoznato ime ili null prati telefon. */
        fun fromName(name: String?): ScreenPosition = entries.firstOrNull { it.name == name } ?: AUTO
    }
}
