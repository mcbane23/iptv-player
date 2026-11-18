package com.bane23.iptvplayer.ui.common.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/*
 * Delovi kontrola plejera koje dele telefon i TV, da bi oba plejera izgledala isto: gore nazad i
 * naslov u tamnom okviru, dole jedna zaobljena traka (dugmad, vreme, traka napretka, rezolucija),
 * a ispod nje red dugmadi sa nazivom. Sve stoji na tamnim poluprovidnim pločicama sa tankim
 * okvirom, bez zatamnjenja cele slike. Na TV-u fokusirano dugme dobija prsten u boji teme.
 */

private val PanelBackground = Color.Black.copy(alpha = 0.6f)
private val PanelBorder = Color.White.copy(alpha = 0.16f)
private val ChipShape = RoundedCornerShape(10.dp)

/** Veličina dugmadi sa ikonicom. Donja traka na širokom ekranu je smanjuje da bi traka bila niža. */
private val LocalPlayerButtonSize = compositionLocalOf { 48.dp }

/** Tamna poluprovidna pločica sa tankim okvirom, podloga za sve kontrole plejera. */
internal fun Modifier.playerPanel(shape: Shape): Modifier =
    background(PanelBackground, shape).border(1.dp, PanelBorder, shape)

/** Veličina teksta u dugmadima ispod trake. Naslov i sat koriste istu, da gornji red ne odskače. */
private val ChipTextSize = 12.sp
private val ChipHeight = 32.dp

/**
 * Naslov u tamnom okviru, u jednom redu, iste visine i veličine teksta kao dugmad ispod trake.
 * [subtitle] (emisija koja je sada) ide iza naslova. Okvir je širok koliko i tekst, a [modifier]
 * određuje najveći prostor koji sme da zauzme.
 */
@Composable
fun PlayerTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        PlayerLabel(text = if (subtitle.isNullOrBlank()) title else "$title  ·  $subtitle")
    }
}

/** Sat u istom tamnom okviru kao naslov, u gornjem desnom uglu plejera. */
@Composable
fun PlayerClock(modifier: Modifier = Modifier) {
    val clock by com.bane23.iptvplayer.ui.common.rememberClockText()
    PlayerLabel(text = clock, modifier = modifier)
}

/** Tekst u tamnom okviru koji izgleda kao dugme ispod trake, ali se ne može izabrati. */
@Composable
private fun PlayerLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(ChipHeight)
            .playerPanel(ChipShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = ChipTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Okruglo dugme sa ikonicom. [primary] ima veću ikonicu (puštanje i pauza), a [backdrop] tamni
 * krug iza ikonice, za dugmad koja stoje sama preko slike (nazad, zaključavanje). U fokusu dugme
 * poraste i dobije prsten u boji teme.
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    backdrop: Boolean = false,
    size: Dp = Dp.Unspecified,
) {
    val buttonSize = if (size.isSpecified) size else LocalPlayerButtonSize.current
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.2f else 1f
    Box(
        modifier = modifier
            .size(buttonSize)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                when {
                    focused -> Modifier
                        .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)

                    backdrop -> Modifier.playerPanel(CircleShape)
                    else -> Modifier
                },
            )
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(if (primary) 30.dp else 24.dp),
        )
    }
}

/**
 * Dugme u redu ispod trake (Titlovi, Audio, Format...), sa ikonicom i nazivom. Na uspravnom ekranu
 * (telefon u portretu) ostaje samo ikonica, da ceo red stane u širinu ekrana; naziv tada čita čitač ekrana.
 */
@Composable
fun PlayerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val content = if (active) MaterialTheme.colorScheme.primary else Color.White
    val iconOnly = icon != null && LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    Row(
        modifier = modifier
            .height(if (iconOnly) 36.dp else ChipHeight)
            // U fokusu (TV): isti okvir u boji teme kao kod okruglih dugmadi.
            .then(
                if (focused) {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.85f), ChipShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, ChipShape)
                } else {
                    Modifier.playerPanel(ChipShape)
                },
            )
            .clip(ChipShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = if (iconOnly) label else null,
                tint = content,
                modifier = Modifier.size(if (iconOnly) 20.dp else 16.dp),
            )
        }
        if (!iconOnly) {
            Text(
                text = label,
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = ChipTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = if (icon != null) 6.dp else 0.dp)
                    .widthIn(max = 180.dp),
            )
        }
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
 * u fokusu na TV-u (držanje strelice premotava sve brže, [onSeekEnd] javlja da je strelica puštena).
 * Dok se prevlači, [onPreview] javlja
 * poziciju pod prstom, a premotava se tek kad se prst podigne.
 *
 * Kad je [armed] zadat (TV), traka u fokusu ne premotava dok se ne potvrdi sa OK: strelice levo i
 * desno do tada samo prenose fokus na susedna dugmad. OK uključuje i isključuje premotavanje, a
 * [onArmedChange] javlja promenu. Kad traka izgubi fokus, premotavanje se isključuje.
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (Long?) -> Unit = {},
    stepMs: Long = 10_000L,
    onSeekEnd: () -> Unit = {},
    armed: Boolean? = null,
    onArmedChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val seeking = armed ?: true
    var dragged by remember { mutableStateOf<Float?>(null) }
    val enabled = durationMs > 0
    val fraction = dragged ?: if (enabled) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val active = (focused && seeking) || dragged != null
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .onFocusChanged {
                if (focused && !it.isFocused && armed == true) onArmedChange(false)
                focused = it.isFocused
            }
            .onKeyEvent { event ->
                if (!enabled) return@onKeyEvent false
                if (armed != null && event.key in ConfirmKeys) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        if (armed) onSeekEnd()
                        onArmedChange(!armed)
                    }
                    return@onKeyEvent true
                }
                // Bez potvrde strelice prolaze dalje i pomeraju fokus.
                if (!seeking) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (direction == 0) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> onSeekBy(direction * seekStepFor(event.nativeKeyEvent.repeatCount, stepMs))
                    KeyEventType.KeyUp -> onSeekEnd()
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
            val thumb = if (active || focused) 16.dp else 12.dp
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumb) * fraction)
                    .size(thumb)
                    .clip(CircleShape)
                    // U fokusu bez potvrde: beli prsten. Dok se premotava: pun beli krug.
                    .background(if (focused && seeking) Color.White else accent)
                    .then(if (focused && !seeking) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
            )
        }
    }
}

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Korak premotavanja za strelicu na daljinskom: prvi pritisak je [baseMs] (iz podešavanja), a dok
 * se strelica drži korak raste, da se i dug film brzo pređe.
 */
fun seekStepFor(repeatCount: Int, baseMs: Long = 10_000L): Long = when {
    repeatCount < 6 -> baseMs
    repeatCount < 20 -> maxOf(30_000L, baseMs * 2)
    else -> maxOf(60_000L, baseMs * 4)
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

/**
 * Donja traka plejera, ista na TV-u i telefonu: jedna zaobljena pločica u kojoj su levo dugmad
 * ([leading]), pa vreme, traka i vreme, pa desno ostalo ([trailing]). Na uskom ekranu (telefon
 * uspravno) vremena idu ispod trake, da traka ostane dovoljno duga.
 */
@Composable
fun PlayerTransportBar(
    startLabel: String?,
    endLabel: String?,
    leading: @Composable RowScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    bar: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(
                modifier = Modifier
                    .playerPanel(RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Niska, vitka pločica: dugmad od 40dp, pa je cela traka visoka 44dp.
                CompositionLocalProvider(LocalPlayerButtonSize provides 40.dp) {
                    leading()
                    TimeLabel(startLabel, Modifier.padding(start = 8.dp))
                    bar(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    TimeLabel(endLabel, Modifier.padding(end = 12.dp))
                    trailing()
                }
            }
        } else {
            // Uzak ekran: premotavanje na krajevima, traka u sredini, vremena ispod trake da traka ostane duga.
            Row(
                modifier = Modifier
                    .playerPanel(RoundedCornerShape(28.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    bar(Modifier.fillMaxWidth())
                    if (startLabel != null || endLabel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeLabel(startLabel)
                            Spacer(modifier = Modifier.weight(1f))
                            TimeLabel(endLabel)
                        }
                    }
                }
                trailing()
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Rezolucija slike koja se trenutno pušta (npr. 1920×1080), ili ništa dok se ne zna. */
@Composable
fun PlayerResolution(player: Player, modifier: Modifier = Modifier) {
    var size by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                size = videoSize
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (size.width <= 0 || size.height <= 0) return
    Text(
        text = "${size.width}×${size.height}",
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier,
    )
}
