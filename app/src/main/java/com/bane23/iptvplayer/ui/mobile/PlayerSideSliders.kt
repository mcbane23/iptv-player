package com.bane23.iptvplayer.ui.mobile

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.playerPanel
import kotlin.math.roundToInt

/** Jačina zvuka i osvetljenje ekrana dok je plejer otvoren, obe vrednosti od 0 do 1. */
internal class PlayerLevels(private val activity: Activity?, private val audio: AudioManager) {
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var volume by mutableFloatStateOf(systemVolume())
        private set
    var brightness by mutableFloatStateOf(
        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f,
    )
        private set

    private fun systemVolume() = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    /** Zvuk je možda u međuvremenu promenjen tasterima telefona. */
    fun refreshVolume() {
        volume = systemVolume()
    }

    fun changeVolume(delta: Float) {
        volume = (volume + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
    }

    fun changeBrightness(delta: Float) {
        brightness = (brightness + delta).coerceIn(0.01f, 1f)
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
    }

    /** Vraća osvetljenje na sistemsko kad se plejer zatvori. */
    fun restoreBrightness() {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        }
    }
}

/** Pamti se na nivou ekrana plejera, da izabrano osvetljenje ostane i kad se kontrole sklone. */
@Composable
internal fun rememberPlayerLevels(): PlayerLevels {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val levels = remember(activity) {
        PlayerLevels(activity, context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    }
    DisposableEffect(levels) {
        onDispose { levels.restoreBrightness() }
    }
    return levels
}

/**
 * Dve tanke uspravne trake uz ivice, u stilu vremenske trake: levo osvetljenje, desno jačina zvuka.
 * Svaka se posebno uključuje u Podešavanjima i obe su isključene dok ih korisnik ne uključi.
 * Vide se samo uz ostale kontrole; prevlačenje van njih ne menja ništa. [onInteraction] se javlja
 * na svaki dodir, da se kontrole ne sklone dok se traka pomera.
 */
@Composable
internal fun BoxScope.PlayerSideSliders(
    levels: PlayerLevels,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onInteraction: () -> Unit,
) {
    val showBrightness by settingsViewModel.playerBrightnessSlider.collectAsStateWithLifecycle()
    val showVolume by settingsViewModel.playerVolumeSlider.collectAsStateWithLifecycle()
    LaunchedEffect(levels) { levels.refreshVolume() }
    if (showBrightness) SideSlider(
        icon = AppIcons.Brightness,
        description = stringResource(R.string.gesture_brightness),
        fraction = levels.brightness,
        onChange = {
            levels.changeBrightness(it)
            onInteraction()
        },
        onInteraction = onInteraction,
        modifier = Modifier.align(Alignment.CenterStart),
    )
    if (showVolume) SideSlider(
        icon = AppIcons.VolumeUp,
        description = stringResource(R.string.gesture_volume),
        fraction = levels.volume,
        onChange = {
            levels.changeVolume(it)
            onInteraction()
        },
        onInteraction = onInteraction,
        modifier = Modifier.align(Alignment.CenterEnd),
    )
}

/**
 * Koliko je panel sa podacima o strimu odmaknut od leve ivice. Kad je traka za osvetljenje
 * uključena, panel stoji desno od nje, da se ne preklapaju; stoji tu i dok su kontrole sklonjene,
 * da ne poskakuje.
 */
@Composable
internal fun streamInfoStartPadding(settingsViewModel: SettingsViewModel = hiltViewModel()): Dp {
    val showBrightness by settingsViewModel.playerBrightnessSlider.collectAsStateWithLifecycle()
    return if (showBrightness) SLIDER_SIDE_SPACE + 8.dp else 16.dp
}

@Composable
private fun SideSlider(
    icon: ImageVector,
    description: String,
    fraction: Float,
    onChange: (Float) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val percent = (fraction * 100).roundToInt()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxHeight()
            .safeDrawingPadding()
            // Mesto za gornji red dugmadi i donju traku sa kontrolama, da se ništa ne preklapa.
            .padding(start = SLIDER_EDGE_PADDING, end = SLIDER_EDGE_PADDING, top = SLIDER_TOP_SPACE, bottom = SLIDER_BOTTOM_SPACE),
    ) {
        // Vrednost se vidi samo dok se traka pomera; mesto ostaje zauzeto da traka ne poskakuje.
        Text(
            text = "$percent%",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.alpha(if (dragging) 1f else 0f),
        )
        // Ista tamna pločica sa tankim okvirom kao donja traka sa kontrolama.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .playerPanel(RoundedCornerShape(percent = 50))
                .padding(top = 14.dp, bottom = 10.dp),
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = SLIDER_MAX_HEIGHT)
                    .width(SLIDER_TOUCH_WIDTH)
                    .fillMaxHeight()
                    // Uz ivicu ekrana sistem inače hvata potez kao gest Nazad.
                    .systemGestureExclusion()
                    .semantics { contentDescription = "$description $percent%" }
                    .pointerInput(Unit) { detectTapGestures { onInteraction() } }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // Cela dužina trake je ceo opseg, pa tačka prati prst.
                                onChange(-dragAmount / size.height)
                            },
                        )
                    },
            ) {
                val trackWidth = if (dragging) 5.dp else 3.dp
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .width(trackWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(trackWidth / 2))
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .width(trackWidth)
                            .fillMaxHeight(fraction.coerceIn(0f, 1f))
                            .background(accent),
                    )
                }
                val thumb = if (dragging) 14.dp else 10.dp
                Box(
                    modifier = Modifier
                        .offset(y = -(maxHeight - thumb) * fraction.coerceIn(0f, 1f))
                        .size(thumb)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private val SLIDER_TOUCH_WIDTH = 40.dp
private val SLIDER_EDGE_PADDING = 8.dp

/** Širina koju traka zauzima uz ivicu ekrana, sa okvirom pločice. */
private val SLIDER_SIDE_SPACE = SLIDER_EDGE_PADDING + SLIDER_TOUCH_WIDTH + 4.dp
private val SLIDER_MAX_HEIGHT = 160.dp
private val SLIDER_TOP_SPACE = 76.dp
private val SLIDER_BOTTOM_SPACE = 164.dp
