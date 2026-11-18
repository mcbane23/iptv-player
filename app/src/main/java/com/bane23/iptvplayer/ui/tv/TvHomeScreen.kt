package com.bane23.iptvplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.subtitles.isOpenSubtitlesConfigured
import com.bane23.iptvplayer.data.trakt.isTraktConfigured
import com.bane23.iptvplayer.ui.common.ExitOnDoubleBack
import com.bane23.iptvplayer.ui.common.CategorySection
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.recording.ActiveRecordingsViewModel
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel

/** Sekcije koje se otvaraju u početnom ekranu, pored bočnog menija. */
private enum class TvTab { LIVE, MOVIES, SERIES, PLAYLISTS, SETTINGS }

/** Širina skupljenog menija (samo ikonice) i raširenog (ikonice i nazivi). */
private val RailCollapsed = 88.dp
private val RailExpanded = 240.dp

/**
 * Početni ekran na TV-u: uzan bočni meni sa ikonicama levo i izabrana sekcija desno.
 * Meni se raširi (prikaže nazive) čim fokus pređe na njega, preko sadržaja, da se sadržaj ne pomera.
 * Pretraga i Vodič otvaraju poseban ekran; ostale stavke menjaju sekciju desno.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    liveTvViewModel: LiveTvViewModel,
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onSearch: () -> Unit,
    onSearchAll: () -> Unit,
    onGuide: (playlistId: Long) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    onSearchVod: (series: Boolean) -> Unit,
    onAddPlaylist: () -> Unit,
    onAddFromPhone: () -> Unit,
    onEditPlaylist: (Long) -> Unit,
    onCategories: (CategorySection) -> Unit,
    onParental: () -> Unit,
    onTrakt: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onRecordings: () -> Unit,
    onMultiView: () -> Unit,
    recordingsViewModel: ActiveRecordingsViewModel = hiltViewModel(),
) {
    val recording by recordingsViewModel.active.collectAsStateWithLifecycle()
    val liveState by liveTvViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TvTab.LIVE) }
    LaunchedEffect(liveState.loading, liveState.hasVod) {
        if (!liveState.loading && !liveState.hasVod && (tab == TvTab.MOVIES || tab == TvTab.SERIES)) {
            tab = TvTab.LIVE
        }
    }
    // Nazad iz druge sekcije prvo vraća na Uživo, tek odatle izlazi iz aplikacije.
    BackHandler(enabled = tab != TvTab.LIVE) { tab = TvTab.LIVE }
    ExitOnDoubleBack(enabled = tab == TvTab.LIVE)
    val holder = rememberSaveableStateHolder()
    val playlistId = liveState.playlistId
    val openLiveCategories: () -> Unit = { onCategories(CategorySection.LIVE) }

    var railFocused by remember { mutableStateOf(false) }
    // Bez animacije širine: animacija ponovo raspoređuje i crta meni sa senkom u svakom kadru,
    // što na slabijim TV uređajima usporava prvi pomeraj fokusa posle ulaska u meni.
    val railWidth = if (railFocused) RailExpanded else RailCollapsed
    val selectedFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Izlazak iz menija u sekciju. Kad desno od stavke nema ničega (prazan ekran: dugme je levo,
    // ispod raširenog menija), fokus ide na prvi element sekcije, inače bi meni ostao raširen.
    val leaveRail: () -> Boolean = {
        focusManager.moveFocus(FocusDirection.Right) || contentFocus.tryRequestFocus()
    }
    // Nazad dok je fokus u meniju prvo sklanja meni.
    BackHandler(enabled = railFocused) {
        if (!leaveRail() && tab != TvTab.LIVE) tab = TvTab.LIVE
    }
    // Stavka „Snima se” ostaje u meniju dok je fokus na njoj i kad se snimanje završi, da fokus ne nestane.
    var recordingFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary),
    ) {
        // Sekcije imaju svoje margine za overscan (48 dp), pa im ovde treba samo ostatak širine menija.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = RailCollapsed - 32.dp)
                .focusRequester(contentFocus)
                .focusGroup(),
        ) {
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    TvTab.LIVE -> TvLiveTvScreen(
                        onPlay = onPlay,
                        onManagePlaylists = { tab = TvTab.PLAYLISTS },
                        onSearch = onSearch,
                        onCategories = { onCategories(CategorySection.LIVE) },
                        viewModel = liveTvViewModel,
                    )

                    // Spisak pripada aktivnoj listi, pa se posle promene liste učitava iznova.
                    TvTab.MOVIES -> TvVodScreen(
                        series = false,
                        onOpen = { onOpenVod(it, false) },
                        onSearch = { onSearchVod(false) },
                        onCategories = { onCategories(CategorySection.MOVIES) },
                        viewModel = hiltViewModel<VodBrowseViewModel>(key = "movies-$playlistId"),
                    )

                    TvTab.SERIES -> TvVodScreen(
                        series = true,
                        onOpen = { onOpenVod(it, true) },
                        onSearch = { onSearchVod(true) },
                        onCategories = { onCategories(CategorySection.SERIES) },
                        viewModel = hiltViewModel<VodBrowseViewModel>(key = "series-$playlistId"),
                    )

                    TvTab.PLAYLISTS -> TvPlaylistsScreen(
                        onAdd = onAddPlaylist,
                        onAddFromPhone = onAddFromPhone,
                        onEdit = onEditPlaylist,
                    )

                    TvTab.SETTINGS -> TvSettingsScreen(
                        onCategories = openLiveCategories.takeIf { playlistId != null },
                        onParental = onParental.takeIf { playlistId != null },
                        onTrakt = onTrakt.takeIf { isTraktConfigured },
                        onOpenSubtitles = onOpenSubtitles.takeIf { isOpenSubtitlesConfigured },
                        onRecordings = onRecordings,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxHeight()
                .width(railWidth)
                .shadow(if (railFocused) 24.dp else 0.dp)
                .background(MaterialTheme.colorScheme.surface)
                .onFocusChanged { railFocused = it.hasFocus }
                .onKeyEvent { event ->
                    if (event.key != Key.DirectionRight) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) leaveRail()
                    true
                }
                // Kad fokus uđe u meni, ide na izabranu sekciju, a ne na najbližu stavku.
                .focusRestorer(selectedFocus)
                .focusGroup()
                // Margine zbog overscan-a na televizorima.
                .padding(horizontal = 16.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Dok se ne zna da li postoji lista, stavke se ne crtaju: inače se pri pokretanju na
            // trenutak vidi kratak meni (kao bez liste), pa tek onda ceo.
            if (liveState.loading) return@Column
            if (recording.isNotEmpty() || recordingFocused) {
                val single = recording.singleOrNull()
                RailItem(
                    icon = AppIcons.Record,
                    label = when {
                        single != null -> stringResource(R.string.recording_home_one, single.channelName)
                        recording.isNotEmpty() -> stringResource(R.string.recording_home_many, recording.size)
                        else -> stringResource(R.string.recordings_title)
                    },
                    expanded = railFocused,
                    onClick = onRecordings,
                    modifier = Modifier.onFocusChanged { recordingFocused = it.isFocused },
                    tint = if (recording.isNotEmpty()) MaterialTheme.colorScheme.error else null,
                )
            }
            if (playlistId != null) {
                RailItem(AppIcons.Search, stringResource(R.string.action_search), expanded = railFocused, onClick = onSearchAll)
            }
            RailItem(
                icon = AppIcons.LiveTv,
                label = stringResource(R.string.nav_live),
                expanded = railFocused,
                selected = tab == TvTab.LIVE,
                onClick = { tab = TvTab.LIVE },
                modifier = if (tab == TvTab.LIVE) Modifier.focusRequester(selectedFocus) else Modifier,
            )
            if (playlistId != null) {
                RailItem(
                    icon = AppIcons.Guide,
                    label = stringResource(R.string.guide_title),
                    expanded = railFocused,
                    onClick = { onGuide(playlistId) },
                )
                RailItem(
                    icon = AppIcons.MultiView,
                    label = stringResource(R.string.multiview_title),
                    expanded = railFocused,
                    onClick = onMultiView,
                )
            }
            if (liveState.hasVod) {
                RailItem(
                    icon = AppIcons.Movies,
                    label = stringResource(R.string.vod_movies),
                    expanded = railFocused,
                    selected = tab == TvTab.MOVIES,
                    onClick = { tab = TvTab.MOVIES },
                    modifier = if (tab == TvTab.MOVIES) Modifier.focusRequester(selectedFocus) else Modifier,
                )
                RailItem(
                    icon = AppIcons.Series,
                    label = stringResource(R.string.vod_series),
                    expanded = railFocused,
                    selected = tab == TvTab.SERIES,
                    onClick = { tab = TvTab.SERIES },
                    modifier = if (tab == TvTab.SERIES) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            RailItem(
                icon = AppIcons.Playlists,
                label = stringResource(R.string.playlists_title),
                expanded = railFocused,
                selected = tab == TvTab.PLAYLISTS,
                onClick = { tab = TvTab.PLAYLISTS },
                modifier = if (tab == TvTab.PLAYLISTS) Modifier.focusRequester(selectedFocus) else Modifier,
            )
            RailItem(
                icon = AppIcons.Settings,
                label = stringResource(R.string.settings_title),
                expanded = railFocused,
                selected = tab == TvTab.SETTINGS,
                onClick = { tab = TvTab.SETTINGS },
                modifier = if (tab == TvTab.SETTINGS) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
}

/**
 * Stavka bočnog menija. Fokusirana stavka je u akcentnoj boji sa tamnim sadržajem, izabrana sekcija ima
 * ikonicu u akcentnoj boji, a [tint] daje stavci svoju boju (crvena dok se snima). Naziv se vidi
 * samo kad je meni raširen.
 */
@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val content = when {
        focused -> colors.onPrimary
        tint != null -> tint
        selected -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) colors.primary else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = content, modifier = Modifier.size(24.dp))
        if (expanded) {
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
