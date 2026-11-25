package com.bane23.iptvplayer.ui.common.vod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Javlja [viewModel] kad je sekcija upravo izabrana iz menija, da se vrati na poslednju izabranu
 * kategoriju umesto na Favorite. Početni ekran briše sačuvano stanje sekcije kad se ode na drugu, pa je
 * oznaka tada nova; pri povratku sa detalja filma ili serije oznaka je sačuvana i ništa se ne menja.
 */
@Composable
fun OpenedFromMenu(viewModel: VodBrowseViewModel) {
    var handled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!handled) {
            handled = true
            viewModel.openedFromMenu()
        }
    }
}
