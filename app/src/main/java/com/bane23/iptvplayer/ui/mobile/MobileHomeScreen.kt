package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel

/** Kartice donje trake na telefonu. */
private enum class MobileTab(val icon: ImageVector, val label: Int) {
    LIVE(AppIcons.LiveTv, R.string.nav_live),
    MOVIES(AppIcons.Movies, R.string.vod_movies),
    SERIES(AppIcons.Series, R.string.vod_series),
    SETTINGS(AppIcons.Settings, R.string.settings_title),
}

/**
 * Početni ekran na telefonu: sekcije Uživo, Filmovi, Serije i Podešavanja u donjoj traci.
 * Filmovi i Serije postoje samo za Xtream naloge. Stanje svake kartice (skrol, izabrana kategorija)
 * ostaje sačuvano pri prelasku na drugu karticu i pri povratku sa drugog ekrana.
 */
@Composable
fun MobileHomeScreen(
    liveTvViewModel: LiveTvViewModel,
    onOpenCategory: (playlistId: Long, categoryId: Long?, title: String) -> Unit,
    onSearch: () -> Unit,
    onGuide: (playlistId: Long) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    onPlaylists: () -> Unit,
    onCategories: () -> Unit,
) {
    val liveState by liveTvViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MobileTab.LIVE) }
    val tabs = if (liveState.hasVod) MobileTab.entries else listOf(MobileTab.LIVE, MobileTab.SETTINGS)
    // Kad aktivna lista nema filmove i serije, kartica koja je nestala vraća na Uživo.
    LaunchedEffect(liveState.loading, liveState.hasVod) {
        if (!liveState.loading && tab !in tabs) tab = MobileTab.LIVE
    }
    val holder = rememberSaveableStateHolder()
    // Nazad iz druge kartice prvo vraća na Uživo, tek odatle izlazi iz aplikacije.
    BackHandler(enabled = tab != MobileTab.LIVE) { tab = MobileTab.LIVE }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(imageVector = item.icon, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(item.label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        // Ekrani u karticama imaju svoj Scaffold; umeci (status bar, donja traka) se ovde troše
        // da ih ne bi dodali još jednom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            // Spisak filmova i serija pripada aktivnoj listi, pa se posle promene liste učitava iznova.
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    MobileTab.LIVE -> MobileLiveTvScreen(
                        onOpenCategory = onOpenCategory,
                        onManagePlaylists = onPlaylists,
                        onSearch = onSearch,
                        onGuide = onGuide,
                        viewModel = liveTvViewModel,
                    )

                    MobileTab.MOVIES -> MobileVodScreen(
                        series = false,
                        onOpen = { onOpenVod(it, false) },
                        viewModel = hiltViewModel<VodBrowseViewModel>(key = "movies-${liveState.playlistId}"),
                    )

                    MobileTab.SERIES -> MobileVodScreen(
                        series = true,
                        onOpen = { onOpenVod(it, true) },
                        viewModel = hiltViewModel<VodBrowseViewModel>(key = "series-${liveState.playlistId}"),
                    )

                    MobileTab.SETTINGS -> MobileSettingsScreen(
                        onPlaylists = onPlaylists,
                        onCategories = onCategories.takeIf { liveState.playlistId != null },
                    )
                }
            }
        }
    }
}
