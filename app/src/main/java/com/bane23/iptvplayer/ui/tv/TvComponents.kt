package com.bane23.iptvplayer.ui.tv

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bane23.iptvplayer.ui.common.userActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import com.bane23.iptvplayer.R
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass
import com.bane23.iptvplayer.ui.common.theme.glassSelected
import com.bane23.iptvplayer.ui.common.theme.glassFocused
import com.bane23.iptvplayer.ui.common.theme.glassState
import com.bane23.iptvplayer.ui.common.theme.focusHalo
import androidx.compose.foundation.BorderStroke

/**
 * Meni preko slike u plejeru (titlovi, zvuk, podešavanja, kanali…) drži fokus dok je otvoren:
 * strelica sa prve ili poslednje stavke, ili u stranu, ne izlazi iz menija na plejer iza njega.
 * Nazad i fokus koji ekran sam postavi (kad se meni zatvori) i dalje prolaze. Svaki novi meni u
 * plejeru treba da ima ovaj modifikator.
 */
fun Modifier.tvKeepFocus(): Modifier = this
    .focusProperties {
        onExit = {
            when (requestedFocusDirection) {
                FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right,
                FocusDirection.Next, FocusDirection.Previous,
                -> cancelFocusChange()
            }
        }
    }
    .focusGroup()

/**
 * Red u listi na TV-u: dobija fokus D-padom, OK ga bira. Izgled „Staklo”: fokusiran red je pun u
 * akcentnoj boji, sa tamnim sadržajem i mekim oreolom, a [selected] red (npr. trenutna kategorija
 * ili kanal) tanju ivicu i tekst u akcentnoj boji. [content] dobija boju teksta koja odgovara podlozi.
 *
 * Sa [card] (podrazumevano, u svim spiskovima na TV-u isti izgled) red je staklena pločica; bez
 * toga red nema podlogu dok nije u fokusu ili izabran (stavke u meniju koji je i sam staklen).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    container: Color = Color.Transparent,
    card: Boolean = true,
    verticalPadding: Dp = 10.dp,
    // Naziv reda za usluge pristupačnosti. Kad je zadat, sadržaj reda je za njih jedna celina sa tim
    // nazivom, umesto desetak delova (logo, tekstovi, traka); to bitno ubrzava listanje na TV-u na kome
    // radi neka takva usluga (zatamnjivanje ekrana, launcher), jer se opis ekrana pravi pri svakom koraku.
    description: String? = null,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        focused -> colors.onPrimary
        selected -> colors.primary
        else -> colors.onSurface
    }
    val shape = Glass.RowShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusHalo(focused, colors.primary)
            .clip(shape)
            .then(
                if (card || focused || selected) {
                    Modifier.glassState(colors.primary, focused, selected, shape)
                } else {
                    Modifier.background(container)
                },
            )
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = verticalPadding)
            // Red samo sa tekstom je visok kao red sa prekidačem, da svi redovi spiska budu isti.
            .heightIn(min = TvSwitchHeight),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            if (description == null) {
                content(contentColor)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().tvItemSemantics(description),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { content(contentColor) },
                )
            }
        },
    )
}

/**
 * Stavka spiska je za usluge pristupačnosti jedna celina sa nazivom [description]: delovi unutar nje
 * se ne opisuju posebno. Na TV-u sa uključenom takvom uslugom Compose pri svakom koraku fokusa iznova
 * prolazi kroz sve opisane delove ekrana, pa manje delova znači glatko listanje.
 */
fun Modifier.tvItemSemantics(description: String): Modifier =
    clearAndSetSemantics { contentDescription = description }

/**
 * Red sa jednim tekstom i, po želji, tekstom na desnoj strani (npr. broj kanala). Kad je zadat
 * [checked], red je podešavanje uključeno/isključeno i desno ima prekidač; OK ga menja.
 */
@Composable
fun TvTextRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
    checked: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    card: Boolean = true,
    /** Krug u ovoj boji pre naziva: primer boje ili teme u spisku vrednosti. */
    swatch: Color? = null,
) {
    TvListRow(
        onClick = onClick,
        modifier = if (checked != null) {
            modifier.semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
        } else {
            modifier
        },
        selected = selected,
        onFocused = onFocused,
        onLongClick = onLongClick,
        card = card,
        description = if (trailing != null) "$text, $trailing" else text,
    ) { color ->
        if (icon != null) {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
            )
        }
        if (swatch != null) {
            // Tanak okvir u boji teksta, da se i tamna boja teme vidi na tamnoj podlozi.
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp)
                    .background(swatch, androidx.compose.foundation.shape.CircleShape)
                    .border(1.5.dp, color.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape),
            )
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = color.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Duga vrednost (npr. tri jezika) ne sme da istisne naziv reda.
                modifier = Modifier
                    .padding(start = 12.dp)
                    .widthIn(max = 200.dp),
            )
        }
        if (checked != null) {
            val scheme = MaterialTheme.colorScheme
            TvSwitch(checked = checked, color = color, accent = scheme.primary, onAccentRow = color == scheme.onPrimary)
        }
    }
}

/** Visina prekidača; ujedno najmanja visina sadržaja svakog reda u spisku na TV-u. */
private val TvSwitchHeight = 22.dp

/**
 * Prekidač u redu podešavanja. Samo prikazuje stanje (fokus i OK pripadaju celom redu). Uključen
 * je pun, u akcentnoj boji, sa kružićem desno; isključen je samo okvir u boji teksta sa kružićem
 * levo. Na redu čija je podloga puna akcentna ([onAccentRow]) boje su obrnute: tamna podloga i
 * akcentni kružić.
 */
@Composable
private fun TvSwitch(checked: Boolean, color: Color, accent: Color, onAccentRow: Boolean) {
    val shape = RoundedCornerShape(11.dp)
    val track = if (onAccentRow) color else accent
    val thumb = when {
        !checked -> color.copy(alpha = 0.6f)
        onAccentRow -> accent
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(width = 40.dp, height = TvSwitchHeight)
            .then(
                if (checked) Modifier.background(track, shape) else Modifier.border(2.dp, color.copy(alpha = 0.6f), shape),
            )
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.size(14.dp).background(thumb, CircleShape))
    }
}

/**
 * Glavno dugme na TV-u. Bez fokusa je staklena pločica sa tekstom u akcentnoj boji, a u fokusu je puno
 * u akcentnoj boji, isto kao fokusiran red u listi.
 */
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = Glass.FillStrong,
            contentColor = scheme.primary,
            focusedContainerColor = scheme.primary,
            focusedContentColor = scheme.onPrimary,
            pressedContainerColor = scheme.primary,
            pressedContentColor = scheme.onPrimary,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Glass.Border)),
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
        content = content,
    )
}

/** Sporedno dugme na TV-u: bez fokusa samo okvir, u fokusu puno u akcentnoj boji. */
@Composable
fun TvOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = OutlinedButtonDefaults.colors(
            focusedContainerColor = scheme.primary,
            focusedContentColor = scheme.onPrimary,
            pressedContainerColor = scheme.primary,
            pressedContentColor = scheme.onPrimary,
        ),
        border = OutlinedButtonDefaults.border(
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
        content = content,
    )
}

/**
 * Kratak meni radnji uz desnu ivicu ekrana, u svom prozoru (npr. posle dugog pritiska na
 * kategoriju). OK bira i zatvara, Nazad samo zatvara.
 */
@Composable
fun TvActionDialog(title: String, options: List<String>, onSelect: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        Box(modifier = Modifier.fillMaxSize().userActivity(), contentAlignment = Alignment.CenterEnd) {
            TvChoicePanel(title = title, options = options, onSelect = onSelect, onClose = onClose)
        }
    }
}

/**
 * Izbor filtera u svom prozoru na sredini ekrana. Vrednosti se menjaju strelicama levo i desno, a
 * prekidač tasterom OK, kao u meniju „Podešavanja” plejera; Nazad zatvara.
 */
@Composable
fun TvFilterDialog(
    title: String,
    choices: List<com.bane23.iptvplayer.ui.common.SettingChoice>,
    resetLabel: String,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    // Otvoren dugačak spisak (zemlje): redni broj podešavanja, ili -1. Nazad iz spiska vraća na filter.
    var picking by remember { mutableStateOf(-1) }
    var lastPicked by remember { mutableStateOf(0) }
    Dialog(
        onDismissRequest = { if (picking >= 0) picking = -1 else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        Box(modifier = Modifier.fillMaxSize().userActivity(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .widthIn(min = 420.dp, max = 420.dp)
                    .background(MaterialTheme.colorScheme.surface, Glass.CardShape)
                    .border(1.dp, Glass.Border, Glass.CardShape)
                    .padding(12.dp),
            ) {
                val picked = choices.getOrNull(picking)
                Text(
                    text = picked?.title ?: title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
                )
                if (picked != null) {
                    TvOptionList(choice = picked, onDone = { picking = -1 })
                } else {
                    TvQuickSettingsPanel(
                        choices = choices,
                        actions = listOf(resetLabel to onReset),
                        focusIndex = lastPicked,
                        onOpenList = {
                            lastPicked = it
                            picking = it
                        },
                    )
                }
            }
        }
    }
}

/**
 * Dugačak spisak vrednosti jednog podešavanja (zemlje u filteru): polje za pretragu i spisak po
 * abecedi. OK bira i vraća na filter. Fokus kreće sa izabrane vrednosti.
 */
@Composable
private fun TvOptionList(choice: com.bane23.iptvplayer.ui.common.SettingChoice, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(choice.options, query) { com.bane23.iptvplayer.ui.common.matchingOptions(choice.options, query) }
    val start = remember { shown.indexOf(choice.selected).coerceAtLeast(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (startFocus.tryRequestFocus()) return@LaunchedEffect
            androidx.compose.runtime.withFrameNanos { }
        }
    }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.tvKeepFocus(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        TvFilterField(value = query, onChange = { query = it }, placeholder = stringResource(R.string.filter_search))
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 330.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            items(shown.size, key = { shown[it] }) { position ->
                val index = shown[position]
                TvTextRow(
                    text = choice.options[index],
                    onClick = {
                        choice.onSelect(index)
                        onDone()
                    },
                    modifier = if (position == start && query.isEmpty()) Modifier.focusRequester(startFocus) else Modifier,
                    selected = index == choice.selected,
                )
            }
        }
    }
}

/**
 * Traži fokus bez rušenja ako element još nije na ekranu.
 * Vraća true samo kad je fokus zaista prešao na element.
 */
fun FocusRequester.tryRequestFocus(): Boolean {
    val result: Any? = runCatching { requestFocus() }.getOrNull()
    return result != null && result != false
}

/** Strelica ispred naslova u plejeru. Samo podseća da taster Nazad zatvara plejer; ne prima fokus. */
@Composable
fun TitleBackIcon() {
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.6f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        androidx.tv.material3.Icon(
            imageVector = com.bane23.iptvplayer.ui.common.icons.AppIcons.Back,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
