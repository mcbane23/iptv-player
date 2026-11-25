package com.bane23.iptvplayer.ui.mobile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.data.recording.ActiveRecording
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.subtitles.isOpenSubtitlesConfigured
import com.bane23.iptvplayer.data.trakt.isTraktConfigured
import com.bane23.iptvplayer.ui.common.ExitOnDoubleBack
import com.bane23.iptvplayer.ui.common.CategorySection
import com.bane23.iptvplayer.ui.common.AppMessages
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.PlaylistRefreshViewModel
import com.bane23.iptvplayer.ui.common.message
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.SearchAllViewModel
import com.bane23.iptvplayer.ui.common.recording.ActiveRecordingsViewModel
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass

/** Kartice donje trake na telefonu. */
private enum class MobileTab(val icon: ImageVector, val label: Int) {
    LIVE(AppIcons.LiveTv, R.string.nav_live),
    MOVIES(AppIcons.Movies, R.string.vod_movies),
    SERIES(AppIcons.Series, R.string.vod_series),
    SEARCH(AppIcons.Search, R.string.action_search),
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
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onSearch: () -> Unit,
    onGuide: (playlistId: Long, categoryId: Long?) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    onSearchVod: (series: Boolean) -> Unit,
    onPlaylists: () -> Unit,
    onCategories: (CategorySection) -> Unit,
    onParental: () -> Unit,
    onUnlock: () -> Unit,
    onTrakt: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onRecordings: () -> Unit,
    onDownloads: () -> Unit,
    onTransfer: () -> Unit,
    onBackup: () -> Unit,
    onMultiView: () -> Unit,
    recordingsViewModel: ActiveRecordingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val underCutout by settingsViewModel.drawUnderCutout.collectAsStateWithLifecycle()
    val recording by recordingsViewModel.active.collectAsStateWithLifecycle()
    val liveState by liveTvViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MobileTab.LIVE) }
    val tabs = if (liveState.hasVod) MobileTab.entries else listOf(MobileTab.LIVE, MobileTab.SEARCH, MobileTab.SETTINGS)
    // Kad aktivna lista nema filmove i serije, kartica koja je nestala vraća na Uživo.
    LaunchedEffect(liveState.loading, liveState.hasVod) {
        if (!liveState.loading && tab !in tabs) tab = MobileTab.LIVE
    }
    // Dok se ne zna da li lista ima filmove i serije, traka je prazna: inače se pri pokretanju
    // na trenutak vide tri kartice, pa tek onda svih pet.
    val shownTabs = if (liveState.loading) emptyList() else tabs
    val holder = rememberSaveableStateHolder()
    // Podešavanja se posle povratka otvaraju na celom meniju, a Uživo na nedavno gledanim.
    // Filmovi i serije se otvaraju na poslednjoj izabranoj kategoriji, ne na Favoritima ni „Nastavi gledanje” (Bane).
    LaunchedEffect(tab) {
        if (tab != MobileTab.SETTINGS) holder.removeState(MobileTab.SETTINGS.name)
        if (tab != MobileTab.MOVIES) holder.removeState(MobileTab.MOVIES.name)
        if (tab != MobileTab.SERIES) holder.removeState(MobileTab.SERIES.name)
        if (tab != MobileTab.LIVE) {
            holder.removeState(MobileTab.LIVE.name)
            liveTvViewModel.startFresh()
        }
    }
    // Nazad iz druge kartice prvo vraća na Uživo, tek odatle izlazi iz aplikacije.
    BackHandler(enabled = tab != MobileTab.LIVE) { tab = MobileTab.LIVE }
    ExitOnDoubleBack(enabled = tab == MobileTab.LIVE)

    val openLiveCategories: () -> Unit = { onCategories(CategorySection.LIVE) }
    val refreshViewModel: PlaylistRefreshViewModel = hiltViewModel()
    val refreshing by refreshViewModel.refreshing.collectAsStateWithLifecycle()
    RefreshFailedMessage(refreshViewModel)
    val tabContent: @Composable () -> Unit = {
        // Spisak filmova i serija pripada aktivnoj listi, pa se posle promene liste učitava iznova.
        holder.SaveableStateProvider(tab.name) {
            // Povlačenje nadole u Uživo, Filmovima i Serijama osvežava celu aktivnu listu.
            val playlistId = liveState.playlistId.takeIf { tab in PULL_REFRESH_TABS }
            PullRefresh(refreshing = refreshing, onRefresh = playlistId?.let { id -> { refreshViewModel.refresh(id) } }) {
                when (tab) {
                    MobileTab.LIVE -> MobileLiveTvScreen(
                        onPlay = onPlay,
                        onManagePlaylists = onPlaylists,
                        onRestore = onBackup,
                        onSearch = onSearch,
                        onGuide = onGuide,
                        onMultiView = onMultiView,
                        onUnlock = onUnlock,
                        onCategories = { onCategories(CategorySection.LIVE) },
                        viewModel = liveTvViewModel,
                    )

                    MobileTab.MOVIES -> {
                        val viewModel = hiltViewModel<VodBrowseViewModel>(key = "movies-${liveState.playlistId}")
                        LaunchedEffect(viewModel) { refreshViewModel.finished.collect { viewModel.reload() } }
                        MobileVodScreen(
                            series = false,
                            onOpen = { onOpenVod(it, false) },
                            onSearch = { onSearchVod(false) },
                            onCategories = { onCategories(CategorySection.MOVIES) },
                            onUnlock = onUnlock,
                            viewModel = viewModel,
                        )
                    }

                    MobileTab.SERIES -> {
                        val viewModel = hiltViewModel<VodBrowseViewModel>(key = "series-${liveState.playlistId}")
                        LaunchedEffect(viewModel) { refreshViewModel.finished.collect { viewModel.reload() } }
                        MobileVodScreen(
                            series = true,
                            onOpen = { onOpenVod(it, true) },
                            onSearch = { onSearchVod(true) },
                            onCategories = { onCategories(CategorySection.SERIES) },
                            onUnlock = onUnlock,
                            viewModel = viewModel,
                        )
                    }

                    MobileTab.SEARCH -> MobileSearchAllScreen(
                        onPlay = { onPlay(it, null) },
                        onOpenVod = onOpenVod,
                        viewModel = hiltViewModel<SearchAllViewModel>(key = "search-all-${liveState.playlistId}"),
                    )

                    MobileTab.SETTINGS -> MobileSettingsScreen(
                        onPlaylists = onPlaylists,
                        onCategories = openLiveCategories.takeIf { liveState.playlistId != null },
                        onParental = onParental.takeIf { liveState.playlistId != null },
                        onTrakt = onTrakt.takeIf { isTraktConfigured },
                        onOpenSubtitles = onOpenSubtitles.takeIf { isOpenSubtitlesConfigured },
                        onRecordings = onRecordings,
                        onDownloads = onDownloads,
                        onTransfer = onTransfer,
                        onBackup = onBackup,
                    )
                }
            }
        }
    }
    val recordingBar: @Composable () -> Unit = {
        if (recording.isNotEmpty()) {
            RecordingBar(active = recording, onOpen = onRecordings, onStop = recordingsViewModel::stop)
        }
    }

    // Jedan raspored za oba položaja: sadržaj kartice ostaje na istom mestu u stablu, pa okretanje
    // telefona menja samo izgled, a izabrana kartica, kategorija, podmeni i skrol ostaju isti.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        // Položeno, po izboru u podešavanjima, sadržaj ide i ispod isečka za kameru (samo sistemske trake se poštuju).
        contentWindowInsets = when {
            !landscape -> ScaffoldDefaults.contentWindowInsets
            underCutout -> WindowInsets.systemBars
            else -> WindowInsets.safeDrawing
        },
        // Blag sjaj akcentne boje gore levo; ekrani u karticama su providni da se vidi.
        containerColor = Color.Transparent,
        // Providna pozadina ne daje boju teksta, pa se zadaje izričito (inače je crn).
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background),
        bottomBar = {
            if (!landscape) {
                Column {
                    recordingBar()
                    CompactNavBar(tabs = shownTabs, selected = tab, onSelect = { tab = it })
                }
            }
        },
    ) { padding ->
        // Ekrani u karticama imaju svoj Scaffold; umeci (status bar, donja traka) se ovde troše
        // da ih ne bi dodali još jednom.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                // Ekrani u karticama ne smeju sami da se odmaknu od kamere kad je izabrana cela širina.
                .then(if (landscape && underCutout) Modifier.consumeWindowInsets(WindowInsets.displayCutout) else Modifier),
        ) {
            // U landscape-u je visina dragocena: kartice idu u usku traku levo, a ne u donju traku.
            if (landscape) CompactNavRail(tabs = shownTabs, selected = tab, onSelect = { tab = it })
            Column(modifier = Modifier.weight(1f)) {
                if (landscape) recordingBar()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    tabContent()
                }
            }
        }
    }
}

/**
 * Traka iznad donje trake dok se nešto snima, vidljiva u svim karticama. Dodir otvara „Snimci”;
 * kad se snima jedan kanal, snimanje može da se zaustavi odmah, bez odlaska na drugi ekran.
 */
private val NavRailShape = RoundedCornerShape(28.dp)

private val PULL_REFRESH_TABS = setOf(MobileTab.LIVE, MobileTab.MOVIES, MobileTab.SERIES)

/** Sadržaj kartice koji se povlačenjem nadole osvežava; bez [onRefresh] je običan okvir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRefresh(refreshing: Boolean, onRefresh: (() -> Unit)?, content: @Composable () -> Unit) {
    if (onRefresh == null) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
    } else {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { content() }
    }
}

/** Kratka poruka kad osvežavanje povlačenjem ne uspe. Na ekranu ostaje dosadašnji sadržaj. */
@Composable
private fun RefreshFailedMessage(viewModel: PlaylistRefreshViewModel) {
    var failed by remember { mutableStateOf<PlaylistError?>(null) }
    LaunchedEffect(viewModel) { viewModel.errors.collect { failed = it } }
    failed?.let { error ->
        val text = error.copy(detail = null).message()
        LaunchedEffect(error) {
            AppMessages.show(text, long = true)
            failed = null
        }
    }
}

@Composable
private fun RecordingBar(active: List<ActiveRecording>, onOpen: () -> Unit, onStop: (Long) -> Unit) {
    val single = active.singleOrNull()
    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Glass.PillShape)
                .glass(Glass.PillShape, strong = true)
                .clickable(onClick = onOpen)
                .height(48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Record,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (single != null) {
                    stringResource(R.string.recording_home_one, single.channelName)
                } else {
                    stringResource(R.string.recording_home_many, active.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            if (single != null) {
                TextButton(onClick = { onStop(single.id) }) {
                    Text(stringResource(R.string.recording_home_stop))
                }
            } else {
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.recordings_title))
                }
            }
        }
    }
}

/**
 * Donji meni kao lebdeća staklena kapsula (izgled „Staklo”), nizak (56 dp) da sadržaju ostane više
 * mesta. Izabranu karticu pokazuje boja ikonice i naziva.
 */
@Composable
private fun CompactNavBar(tabs: List<MobileTab>, selected: MobileTab, onSelect: (MobileTab) -> Unit) {
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(Glass.PillShape)
                .glass(Glass.PillShape, strong = true)
                .selectableGroup(),
        ) {
            tabs.forEach { item ->
                val isSelected = item == selected
                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val interactions = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Bez pravougaonog senčenja cele kolone: dodir se vidi samo na podlozi ikonice.
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactions,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(item) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 26.dp)
                            // Bez podloge oko ikonice (Bane): izabranu karticu pokazuje boja.
                            .clip(RoundedCornerShape(13.dp))
                            .indication(interactions, ripple()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = item.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = stringResource(item.label),
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Meni levo, za telefon u landscape-u: ista lebdeća staklena kapsula kao donji meni (ikonica i
 * naziv ispod), samo uspravno.
 */
@Composable
private fun CompactNavRail(tabs: List<MobileTab>, selected: MobileTab, onSelect: (MobileTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .clip(NavRailShape)
                .glass(NavRailShape, strong = true)
                .selectableGroup(),
            verticalArrangement = Arrangement.Center,
        ) {
            tabs.forEach { item ->
                val isSelected = item == selected
                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val interactions = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        // Bez pravougaonog senčenja cele kolone: dodir se vidi samo na podlozi ikonice.
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactions,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(item) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 26.dp)
                            // Bez podloge oko ikonice (Bane): izabranu karticu pokazuje boja.
                            .clip(RoundedCornerShape(13.dp))
                            .indication(interactions, ripple()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = item.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = stringResource(item.label),
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}
