package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Red u listi na TV-u: dobija fokus D-padom, OK ga bira. Fokusiran red je obojen akcentnom bojom,
 * a [selected] red (npr. trenutna kategorija ili kanal) ostaje blago istaknut i kad nema fokus.
 * [content] dobija boju teksta koja odgovara pozadini.
 */
@Composable
fun TvListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background = when {
        focused -> colors.primary
        selected -> colors.surfaceVariant
        else -> Color.Transparent
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { content(contentColor) },
    )
}

/** Red sa jednim tekstom i, po želji, tekstom na desnoj strani (npr. broj kanala). */
@Composable
fun TvTextRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
) {
    TvListRow(onClick = onClick, modifier = modifier, selected = selected, onFocused = onFocused) { color ->
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
    }
}
