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
import androidx.compose.ui.graphics.luminance
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Red u listi na TV-u: dobija fokus D-padom, OK ga bira. Fokusiran red je obojen akcentnom bojom,
 * a [selected] red (npr. trenutna kategorija ili kanal) ostaje blago istaknut i kad nema fokus.
 * [content] dobija boju teksta koja odgovara pozadini.
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
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background = when {
        focused -> colors.primary
        selected -> colors.surfaceVariant
        else -> container
    }
    val contentColor = if (focused) colors.onPrimary else colors.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
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
        if (checked != null) TvSwitch(checked = checked, color = color)
    }
}

/**
 * Prekidač u redu podešavanja. Samo prikazuje stanje (fokus i OK pripadaju celom redu), u boji
 * teksta reda da se vidi i na fokusiranom redu: uključen je pun sa kružićem desno, isključen
 * je samo okvir sa kružićem levo.
 */
@Composable
private fun TvSwitch(checked: Boolean, color: Color) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(width = 40.dp, height = 22.dp)
            .then(
                if (checked) Modifier.background(color, shape) else Modifier.border(2.dp, color.copy(alpha = 0.6f), shape),
            )
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // Kružić na punoj podlozi mora da bude suprotne svetline od nje.
        val thumb = when {
            !checked -> color.copy(alpha = 0.6f)
            color.luminance() > 0.5f -> Color(0xFF0E1216)
            else -> Color.White
        }
        Box(modifier = Modifier.size(14.dp).background(thumb, androidx.compose.foundation.shape.CircleShape))
    }
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
