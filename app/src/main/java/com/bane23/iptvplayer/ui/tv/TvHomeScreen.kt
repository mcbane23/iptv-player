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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
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
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.TvStartViewModel
import com.bane23.iptvplayer.ui.common.guide.GuideViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.recording.ActiveRecordingsViewModel
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import kotlinx.coroutines.delay
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass
import com.bane23.iptvplayer.ui.common.theme.glassSelected
import com.bane23.iptvplayer.ui.common.theme.glassFocused
import com.bane23.iptvplayer.ui.common.theme.glassState
import com.bane23.iptvplayer.ui.common.theme.focusHalo

/** Sekcije koje se otvaraju u početnom ekranu, pored bočnog menija. */
private enum class TvTab { SEARCH, HOME, LIVE, GUIDE, MOVIES, SERIES, PLAYLISTS, SETTINGS }

/** Širina skupljenog menija (samo ikonice) i raširenog (ikonice i nazivi). */
private val RailCollapsed = 88.dp
private val RailShape = RoundedCornerShape(28.dp)
private val RailItemShape = RoundedCornerShape(18.dp)
private val RailExpanded = 240.dp

/**
 * Početni ekran na TV-u: uzan bočni meni sa ikonicama levo i izabrana sekcija desno.
 * Meni se raširi (prikaže nazive) čim fokus pređe na njega i pomeri sadržaj udesno; strelica na vrhu
 * menija ga prebacuje na samo ikonice (bez širenja) i nazad. Izbor se pamti.
 * Sve stavke (i Pretraga i Vodič) menjaju sekciju desno, pored menija.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    liveTvViewModel: LiveTvViewModel,
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onSearch: () -> Unit,
    onArchive: (channelId: Long) -> Unit,
    onPlayArchive: (channelId: Long, program: com.bane23.iptvplayer.domain.Program) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    onSearchVod: (series: Boolean) -> Unit,
    onAddPlaylist: () -> Unit,
    onAddFromPhone: () -> Unit,
    onEditPlaylist: (Long) -> Unit,
    onCategories: (CategorySection) -> Unit,
    onParental: () -> Unit,
    onUnlock: () -> Unit,
    onTrakt: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onRecordings: () -> Unit,
    onRemoteButtons: () -> Unit,
    onBackup: () -> Unit,
    onMultiView: () -> Unit,
    recordingsViewModel: ActiveRecordingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val iconsOnly by settingsViewModel.tvRailIconsOnly.collectAsStateWithLifecycle()
    val recording by recordingsViewModel.active.collectAsStateWithLifecycle()
    val liveState by liveTvViewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TvTab.HOME) }
    val playlistId = liveState.playlistId
    // Aplikacija se otvara na „Početnoj”; bez liste nje nema, pa se otvara Uživo sa dugmetom za dodavanje.
    val startTab = if (playlistId != null || liveState.loading) TvTab.HOME else TvTab.LIVE
    LaunchedEffect(startTab) {
        if (startTab == TvTab.LIVE && tab == TvTab.HOME) tab = TvTab.LIVE
    }
    LaunchedEffect(liveState.loading, liveState.hasVod) {
        if (!liveState.loading && !liveState.hasVod && (tab == TvTab.MOVIES || tab == TvTab.SERIES)) {
            tab = TvTab.LIVE
        }
    }
    // Nazad iz druge sekcije prvo vraća na početnu sekciju, tek odatle izlazi iz aplikacije.
    // Pri pokretanju, posle povratka dugmetom Nazad i kad se „Početna” izabere iz menija fokus ide pravo
    // na prvu stavku „Početne”, da prvi pritisak ne troši vreme na bočni meni.
    var focusHomeContent by rememberSaveable { mutableStateOf(true) }
    // Dok fokus putuje u sadržaj (pokretanje, povratak sa drugog ekrana, Nazad iz druge sekcije), on na
    // trenutak padne na bočni meni. Meni se za to vreme ne širi i ne boji stavku, da ne zatreperi (Bane).
    // Prestaje čim sadržaj dobije fokus, a najkasnije posle kratkog čekanja, ako sadržaj nema šta da fokusira.
    var railHold by remember { mutableStateOf(true) }
    LaunchedEffect(railHold) {
        if (railHold) {
            delay(RAIL_HOLD_MS)
            railHold = false
        }
    }
    BackHandler(enabled = tab != startTab) {
        focusHomeContent = true
        railHold = true
        tab = startTab
    }
    ExitOnDoubleBack(enabled = tab == startTab)
    val holder = rememberSaveableStateHolder()
    // Podešavanja se posle povratka otvaraju na celom meniju, a Uživo na nedavno gledanim.
    // Filmovi i serije pamte svoju kategoriju.
    LaunchedEffect(tab) {
        if (tab != TvTab.SETTINGS) holder.removeState(TvTab.SETTINGS.name)
        if (tab != TvTab.HOME) focusHomeContent = false
        if (tab != TvTab.LIVE) {
            holder.removeState(TvTab.LIVE.name)
            liveTvViewModel.startFresh()
        }
    }
    val openLiveCategories: () -> Unit = { onCategories(CategorySection.LIVE) }

    var railFocused by remember { mutableStateOf(false) }
    // Bez animacije širine i bez senke: senka i animacija se crtaju u svakom kadru, što na slabijim
    // TV uređajima usporava prvi pomeraj fokusa posle ulaska u meni.
    val railExpanded = railFocused && !iconsOnly && !railHold
    val railWidth = if (railExpanded) RailExpanded else RailCollapsed
    // Raširen meni pomera sadržaj udesno samo kao pomeraj pri crtanju: ništa se ne preslaže, pa je i na
    // slabijem uređaju bez zastoja (desna ivica za to vreme izlazi van ekrana).
    val density = LocalDensity.current
    val slidePx = with(density) { (RailExpanded - RailCollapsed).toPx() }
    val selectedFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Izlazak iz menija u sekciju. Kad desno od stavke nema ničega (prazan ekran: dugme je levo,
    // ispod raširenog menija), fokus ide na prvi element sekcije, inače bi meni ostao raširen.
    val leaveRail: () -> Boolean = {
        focusManager.moveFocus(FocusDirection.Right) || contentFocus.tryRequestFocus()
    }
    // OK na sekciji u meniju otvara je i fokus odmah prelazi u njen sadržaj, kod svih sekcija isto (Bane).
    // Ostale sekcije same uzimaju fokus čim se prikažu; „Početna” to radi preko [focusHomeContent], a
    // sekcija koja je već otvorena se samo napusti kao strelicom Desno.
    val pickTab: (TvTab) -> Unit = { picked ->
        when {
            picked == TvTab.HOME -> {
                focusHomeContent = true
                tab = picked
            }

            picked == tab -> leaveRail()
            else -> tab = picked
        }
    }
    // Nazad dok je fokus u meniju prvo sklanja meni.
    BackHandler(enabled = railFocused) {
        if (!leaveRail() && tab != startTab) tab = startTab
    }
    // Stavka „Snima se” ostaje u meniju dok je fokus na njoj i kad se snimanje završi, da fokus ne nestane.
    var recordingFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background),
    ) {
        // Sekcije imaju svoje margine za overscan (48 dp), pa im ovde treba samo ostatak širine menija.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = RailCollapsed - 32.dp)
                .graphicsLayer { translationX = if (railExpanded) slidePx else 0f }
                .onFocusChanged { if (it.hasFocus) railHold = false }
                .focusRequester(contentFocus)
                .focusGroup(),
        ) {
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    // Dok se ne zna da li lista postoji, ne crta se ništa (kao ni bočni meni).
                    TvTab.HOME -> if (playlistId != null) {
                        TvStartScreen(
                            onPlay = onPlay,
                            onOpenVod = onOpenVod,
                            autoFocus = focusHomeContent,
                            onAutoFocused = { focusHomeContent = false },
                            viewModel = hiltViewModel<TvStartViewModel>(key = "start-$playlistId"),
                        )
                    }

                    TvTab.LIVE -> TvLiveTvScreen(
                        onPlay = onPlay,
                        onManagePlaylists = { tab = TvTab.PLAYLISTS },
                        onRestore = onBackup,
                        onSearch = onSearch,
                        onUnlock = onUnlock,
                        onCategories = { onCategories(CategorySection.LIVE) },
                        viewModel = liveTvViewModel,
                    )

                    // Pretraga svega kao kartica, sa bočnim menijem kao ostale sekcije (Bane).
                    TvTab.SEARCH -> TvSearchAllScreen(
                        onPlay = { channelId -> onPlay(channelId, null) },
                        onOpenVod = onOpenVod,
                    )

                    // Vodič kao kartica, sa bočnim menijem kao ostale sekcije (Bane).
                    TvTab.GUIDE -> if (playlistId != null) {
                        TvGuideScreen(
                            playlistId = playlistId,
                            categoryId = null,
                            onPlay = { channelId -> onPlay(channelId, null) },
                            onArchive = onArchive,
                            onPlayArchive = onPlayArchive,
                            viewModel = hiltViewModel<GuideViewModel>(key = "guide-$playlistId"),
                        )
                    }

                    // Spisak pripada aktivnoj listi, pa se posle promene liste učitava iznova.
                    TvTab.MOVIES -> TvVodScreen(
                        series = false,
                        onOpen = { onOpenVod(it, false) },
                        onSearch = { onSearchVod(false) },
                        onCategories = { onCategories(CategorySection.MOVIES) },
                        onUnlock = onUnlock,
                        viewModel = hiltViewModel<VodBrowseViewModel>(key = "movies-$playlistId"),
                    )

                    TvTab.SERIES -> TvVodScreen(
                        series = true,
                        onOpen = { onOpenVod(it, true) },
                        onSearch = { onSearchVod(true) },
                        onCategories = { onCategories(CategorySection.SERIES) },
                        onUnlock = onUnlock,
                        viewModel = hiltViewModel<VodBrowseViewModel>(key = "series-$playlistId"),
                    )

                    TvTab.PLAYLISTS -> TvPlaylistsScreen(
                        onAdd = onAddPlaylist,
                        onAddFromPhone = onAddFromPhone,
                        onEdit = onEditPlaylist,
                        onRestore = onBackup,
                    )

                    TvTab.SETTINGS -> TvSettingsScreen(
                        onCategories = openLiveCategories.takeIf { playlistId != null },
                        onParental = onParental.takeIf { playlistId != null },
                        onTrakt = onTrakt.takeIf { isTraktConfigured },
                        onOpenSubtitles = onOpenSubtitles.takeIf { isOpenSubtitlesConfigured },
                        onRecordings = onRecordings,
                        onRemoteButtons = onRemoteButtons,
                        onBackup = onBackup,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .zIndex(1f)
                .width(railWidth)
                // Meni je lebdeća staklena kapsula, visoka koliko i njene stavke (izgled „Staklo”).
                .padding(start = 10.dp, top = 16.dp, bottom = 16.dp)
                .then(if (liveState.loading) Modifier else Modifier.glass(RailShape, strong = true))
                .onFocusChanged { railFocused = it.hasFocus }
                .onKeyEvent { event ->
                    if (event.key != Key.DirectionRight) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) leaveRail()
                    true
                }
                // Kad fokus uđe u meni, ide na izabranu sekciju, a ne na najbližu stavku.
                .focusRestorer(selectedFocus)
                .focusGroup()
                // Prva stavka je u visini naslova sekcije (27 dp od vrha ekrana).
                .padding(horizontal = 11.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Dok se ne zna da li postoji lista, stavke se ne crtaju: inače se pri pokretanju na
            // trenutak vidi kratak meni (kao bez liste), pa tek onda ceo.
            if (liveState.loading) return@Column
            // Strelica na vrhu: samo ikonice ili meni koji se širi i pomera sadržaj. Fokus ostaje na njoj.
            RailItem(
                icon = if (iconsOnly) AppIcons.ChevronRight else AppIcons.ChevronLeft,
                label = stringResource(if (iconsOnly) R.string.tv_rail_expand else R.string.tv_rail_collapse),
                expanded = railExpanded,
                showFocus = !railHold,
                onClick = { settingsViewModel.setTvRailIconsOnly(!iconsOnly) },
            )
            if (recording.isNotEmpty() || recordingFocused) {
                val single = recording.singleOrNull()
                RailItem(
                    icon = AppIcons.Record,
                    label = when {
                        single != null -> stringResource(R.string.recording_home_one, single.channelName)
                        recording.isNotEmpty() -> stringResource(R.string.recording_home_many, recording.size)
                        else -> stringResource(R.string.recordings_title)
                    },
                    expanded = railExpanded,
                    showFocus = !railHold,
                    onClick = onRecordings,
                    modifier = Modifier.onFocusChanged { recordingFocused = it.isFocused },
                    tint = if (recording.isNotEmpty()) MaterialTheme.colorScheme.error else null,
                )
            }
            if (playlistId != null) {
                RailItem(
                    icon = AppIcons.Search,
                    label = stringResource(R.string.action_search),
                    expanded = railExpanded,
                    showFocus = !railHold,
                    selected = tab == TvTab.SEARCH,
                    onClick = { pickTab(TvTab.SEARCH) },
                    modifier = if (tab == TvTab.SEARCH) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            if (playlistId != null) {
                RailItem(
                    icon = AppIcons.Home,
                    label = stringResource(R.string.nav_home),
                    expanded = railExpanded,
                    showFocus = !railHold,
                    selected = tab == TvTab.HOME,
                    onClick = { pickTab(TvTab.HOME) },
                    modifier = if (tab == TvTab.HOME) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            RailItem(
                icon = AppIcons.LiveTv,
                label = stringResource(R.string.nav_live),
                expanded = railExpanded,
                showFocus = !railHold,
                selected = tab == TvTab.LIVE,
                onClick = { pickTab(TvTab.LIVE) },
                modifier = if (tab == TvTab.LIVE) Modifier.focusRequester(selectedFocus) else Modifier,
            )
            if (playlistId != null) {
                RailItem(
                    icon = AppIcons.Guide,
                    label = stringResource(R.string.guide_title),
                    expanded = railExpanded,
                    showFocus = !railHold,
                    selected = tab == TvTab.GUIDE,
                    onClick = { pickTab(TvTab.GUIDE) },
                    modifier = if (tab == TvTab.GUIDE) Modifier.focusRequester(selectedFocus) else Modifier,
                )
                RailItem(
                    icon = AppIcons.MultiView,
                    label = stringResource(R.string.multiview_title),
                    expanded = railExpanded,
                    showFocus = !railHold,
                    onClick = onMultiView,
                )
            }
            if (liveState.hasVod) {
                RailItem(
                    icon = AppIcons.Movies,
                    label = stringResource(R.string.vod_movies),
                    expanded = railExpanded,
                    showFocus = !railHold,
                    selected = tab == TvTab.MOVIES,
                    onClick = { pickTab(TvTab.MOVIES) },
                    modifier = if (tab == TvTab.MOVIES) Modifier.focusRequester(selectedFocus) else Modifier,
                )
                RailItem(
                    icon = AppIcons.Series,
                    label = stringResource(R.string.vod_series),
                    expanded = railExpanded,
                    showFocus = !railHold,
                    selected = tab == TvTab.SERIES,
                    onClick = { pickTab(TvTab.SERIES) },
                    modifier = if (tab == TvTab.SERIES) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            RailItem(
                icon = AppIcons.Playlists,
                label = stringResource(R.string.playlists_title),
                expanded = railExpanded,
                showFocus = !railHold,
                selected = tab == TvTab.PLAYLISTS,
                onClick = { pickTab(TvTab.PLAYLISTS) },
                modifier = if (tab == TvTab.PLAYLISTS) Modifier.focusRequester(selectedFocus) else Modifier,
            )
            RailItem(
                icon = AppIcons.Settings,
                label = stringResource(R.string.settings_title),
                expanded = railExpanded,
                showFocus = !railHold,
                selected = tab == TvTab.SETTINGS,
                onClick = { pickTab(TvTab.SETTINGS) },
                modifier = if (tab == TvTab.SETTINGS) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
}

/**
 * Stavka bočnog menija. Fokusirana stavka je puna u akcentnoj boji sa tamnim sadržajem, izabrana
 * sekcija ikonicu u akcentnoj boji na blagoj podlozi, a [tint] daje stavci svoju boju (crvena dok se
 * snima). Naziv se vidi samo kad je meni raširen.
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
    showFocus: Boolean = true,
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Dok fokus samo prolazi kroz meni na putu u sadržaj, stavka se ne boji.
    val focused = hasFocus && showFocus
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
            .clip(RailItemShape)
            .then(
                when {
                    focused -> Modifier.glassFocused(colors.primary, RailItemShape)
                    selected -> Modifier.background(colors.primary.copy(alpha = 0.14f))
                    else -> Modifier
                },
            )
            .onFocusChanged { hasFocus = it.isFocused }
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

/** Najduže čekanje da sadržaj preuzme fokus pre nego što se bočni meni ponaša uobičajeno. */
private const val RAIL_HOLD_MS = 800L
