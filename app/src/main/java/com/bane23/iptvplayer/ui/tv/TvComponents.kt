package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.Text

/**
 * Red u listi na TV-u: dobija fokus D-padom, OK ga bira. Fokusiran red je obojen akcentnom bojom,
 * a [selected] red (npr. trenutna kategorija ili kanal) ostaje blago istaknut i kad nema fokus.
 * [content] dobija boju teksta koja odgovara pozadini.
 *
 * Sa [card] red je zaobljena pločica sa podlogom, kao kategorije na telefonu: fokus je i dalje pun
 * u akcentnoj boji, a izabrani red bez fokusa ima akcentni okvir i tekst, da se na ekranu ne vide
 * dva puna akcentna reda.
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
    card: Boolean = false,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background = when {
        focused -> colors.primary
        selected && card -> colors.primary.copy(alpha = 0.16f)
        selected -> colors.surfaceVariant
        card -> colors.surfaceVariant.copy(alpha = 0.6f)
        else -> container
    }
    val contentColor = when {
        focused -> colors.onPrimary
        selected && card -> colors.primary
        else -> colors.onSurface
    }
    val shape = RoundedCornerShape(if (card) 10.dp else 8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (card && selected && !focused) Modifier.border(1.5.dp, colors.primary, shape) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { content(contentColor) },
    )
}

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
    card: Boolean = false,
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
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (checked != null) {
            val scheme = MaterialTheme.colorScheme
            TvSwitch(checked = checked, color = color, accent = scheme.primary, onAccentRow = color == scheme.onPrimary)
        }
    }
}

/**
 * Prekidač u redu podešavanja. Samo prikazuje stanje (fokus i OK pripadaju celom redu). Uključen
 * je pun, u akcentnoj boji, sa kružićem desno; isključen je samo okvir u boji teksta sa kružićem
 * levo. Na fokusiranom redu, čija je podloga već akcentna ([onAccentRow]), boje su obrnute:
 * tamna podloga i akcentni kružić.
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
            .size(width = 40.dp, height = 22.dp)
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
 * Glavno dugme na TV-u. Bez fokusa je tamno sa tekstom u akcentnoj boji, a u fokusu je puno u
 * akcentnoj boji, isto kao fokusiran red u listi.
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
            containerColor = scheme.surfaceVariant,
            contentColor = scheme.primary,
            focusedContainerColor = scheme.primary,
            focusedContentColor = scheme.onPrimary,
            pressedContainerColor = scheme.primary,
            pressedContentColor = scheme.onPrimary,
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
        border = OutlinedButtonDefaults.border(focusedBorder = Border.None, pressedBorder = Border.None),
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            TvChoicePanel(title = title, options = options, onSelect = onSelect, onClose = onClose)
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
