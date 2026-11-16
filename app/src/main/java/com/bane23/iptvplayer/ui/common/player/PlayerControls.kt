package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Delovi kontrola plejera koje dele telefon i TV, da bi oba plejera izgledala isto:
 * naslov gore, traka sa vremenom i dugmad dole. Na TV-u se fokusirani element vidi kao bela
 * pozadina sa tamnim sadržajem (kao u bočnom meniju), na telefonu se dugmad samo dodiruju.
 */

private val FocusedContent = Color(0xFF0E1216)
private val ChipBackground = Color.White.copy(alpha = 0.14f)

/** Zatamnjenje gore i dole, da se tekst i dugmad vide preko svetle slike. */
@Composable
fun PlayerScrim(modifier: Modifier = Modifier, top: Boolean) {
    val colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(if (top) colors else colors.reversed())),
    )
}

/** Naslov i, po želji, red ispod njega (epizoda, emisija koja je sada). */
@Composable
fun PlayerTitle(title: String, subtitle: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White,
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.8f),
                style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Okruglo dugme sa ikonicom; [primary] je veće dugme za puštanje i pauzu. */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: Dp = if (primary) 56.dp else 44.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        primary -> ChipBackground
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (focused) FocusedContent else Color.White,
            modifier = Modifier.size(if (primary) 32.dp else 26.dp),
        )
    }
}

/** Dugme sa nazivom u redu ispod trake (Titlovi, Audio, Format...). */
@Composable
fun PlayerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val content = when {
        focused -> FocusedContent
        active -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color.White else ChipBackground)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            color = content,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = if (icon != null) 8.dp else 0.dp)
                .widthIn(max = 220.dp),
        )
    }
}

/** Vreme levo ili desno od trake. */
@Composable
fun PlayerTime(timeMs: Long, modifier: Modifier = Modifier) {
    Text(
        text = formatDuration(timeMs),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * Traka napretka koja može da premotava: prevlačenjem ili dodirom na telefonu, levo/desno kad je
 * u fokusu na TV-u (držanje strelice premotava sve brže). Dok se prevlači, [onPreview] javlja
 * poziciju pod prstom, a premotava se tek kad se prst podigne.
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (Long?) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf<Float?>(null) }
    val enabled = durationMs > 0
    val fraction = dragged ?: if (enabled) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val active = focused || dragged != null
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (!enabled) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (direction == 0) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    onSeekBy(direction * seekStepFor(event.nativeKeyEvent.repeatCount))
                }
                true
            }
            .focusable(enabled)
            .pointerInput(durationMs) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeekTo((offset.x / size.width).coerceIn(0f, 1f).times(durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragged = (offset.x / size.width).coerceIn(0f, 1f)
                        onPreview((dragged!! * durationMs).toLong())
                    },
                    onDragEnd = {
                        dragged?.let { onSeekTo((it * durationMs).toLong()) }
                        dragged = null
                        onPreview(null)
                    },
                    onDragCancel = {
                        dragged = null
                        onPreview(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragged = (change.position.x / size.width).coerceIn(0f, 1f)
                        onPreview((dragged!! * durationMs).toLong())
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackHeight = if (active) 5.dp else 3.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(accent),
            )
        }
        if (enabled) {
            val thumb = if (active) 16.dp else 12.dp
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumb) * fraction)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(if (focused) Color.White else accent),
            )
        }
    }
}

/**
 * Korak premotavanja za strelicu na daljinskom: prvi pritisak 10 s, a dok se strelica drži
 * korak raste, da se i dug film brzo pređe.
 */
fun seekStepFor(repeatCount: Int): Long = when {
    repeatCount < 6 -> 10_000L
    repeatCount < 20 -> 30_000L
    else -> 60_000L
}

/** Ista traka kao [PlayerSeekBar], ali samo za prikaz (npr. koliko je emisija odmakla). */
@Composable
fun PlayerProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
