package com.bane23.iptvplayer.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Odredišta u aplikaciji. Zajednička su za telefon i TV, razlikuju se samo ekrani. */
@Serializable
data object Home : NavKey
