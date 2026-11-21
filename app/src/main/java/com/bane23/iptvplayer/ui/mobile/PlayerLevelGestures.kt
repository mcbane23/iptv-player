package com.bane23.iptvplayer.ui.mobile

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.playerPanel
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class PlayerLevel { BRIGHTNESS, VOLUME }

/** Jačina zvuka i osvetljenje ekrana dok je plejer otvoren, obe vrednosti od 0 do 1. */
internal class PlayerLevels(private val activity: Activity?, private val audio: AudioManager) {
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var volume by mutableFloatStateOf(systemVolume())
        private set
    /** Šta se upravo menja prevlačenjem; dok nije `null`, pločica sa vrednošću je na ekranu. */
    var active by mutableStateOf<PlayerLevel?>(null)

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

/** Pamti se na nivou ekrana plejera, da izabrano osvetljenje ostane dok je plejer otvoren. */
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

/** Koja prevlačenja je korisnik ostavio uključena u Podešavanjima. */
internal data class LevelSwipes(val brightness: Boolean, val volume: Boolean)

@Composable
internal fun rememberLevelSwipes(settingsViewModel: SettingsViewModel = hiltViewModel()): LevelSwipes {
    val brightness by settingsViewModel.playerBrightnessSwipe.collectAsStateWithLifecycle()
    val volume by settingsViewModel.playerVolumeSwipe.collectAsStateWithLifecycle()
    return LevelSwipes(brightness, volume)
}

private enum class SwipeTarget { BRIGHTNESS, VOLUME, ZAP }

/**
 * Prevlačenje gore-dole preko slike, radi i dok su kontrole sklonjene: leva petina ekrana menja
 * osvetljenje, desna jačinu zvuka. Sredina, ako je [onZap] zadat, pušta sledeći kanal (nagore) ili
 * prethodni (nadole). Gornja i donja ivica se preskaču jer tamo sistem izvlači svoje trake.
 * Sve je u jednom detektoru: dva odvojena bi jedan drugom otimala potez.
 */
internal fun Modifier.playerSwipeGestures(
    levels: PlayerLevels,
    swipes: LevelSwipes,
    enabled: Boolean,
    onInteraction: () -> Unit,
    onZap: ((next: Boolean) -> Unit)? = null,
): Modifier = pointerInput(levels, swipes, enabled, onZap != null) {
    if (!enabled) return@pointerInput
    val zapThreshold = ZAP_SWIPE_DISTANCE.toPx()
    val edge = SWIPE_EDGE.toPx()
    var target: SwipeTarget? = null
    var dragged = 0f
    detectVerticalDragGestures(
        onDragStart = { start ->
            dragged = 0f
            target = when {
                start.y !in edge..size.height - edge -> null
                start.x < size.width * SWIPE_SIDE -> SwipeTarget.BRIGHTNESS.takeIf { swipes.brightness }
                start.x > size.width * (1 - SWIPE_SIDE) -> SwipeTarget.VOLUME.takeIf { swipes.volume }
                else -> SwipeTarget.ZAP.takeIf { onZap != null }
            }
            // Zvuk je možda u međuvremenu promenjen tasterima telefona.
            if (target == SwipeTarget.VOLUME) levels.refreshVolume()
            levels.active = when (target) {
                SwipeTarget.BRIGHTNESS -> PlayerLevel.BRIGHTNESS
                SwipeTarget.VOLUME -> PlayerLevel.VOLUME
                else -> null
            }
        },
        onDragCancel = {
            target = null
            levels.active = null
        },
        onDragEnd = {
            if (target == SwipeTarget.ZAP && abs(dragged) >= zapThreshold) onZap?.invoke(dragged < 0)
            target = null
            levels.active = null
        },
    ) { change, amount ->
        // Isti put prsta za ceo opseg i kad je telefon uspravan i kad je položen.
        val range = minOf(size.width, size.height) * LEVEL_SWIPE_RANGE
        when (target) {
            SwipeTarget.BRIGHTNESS -> levels.changeBrightness(-amount / range)
            SwipeTarget.VOLUME -> levels.changeVolume(-amount / range)
            SwipeTarget.ZAP -> dragged += amount
            null -> return@detectVerticalDragGestures
        }
        if (target != SwipeTarget.ZAP) onInteraction()
        change.consume()
    }
}

/**
 * Mala pločica gore u sredini koja pokazuje vrednost dok se prevlači, ista za zvuk i osvetljenje.
 * Uvek je na istom mestu, pa je prst ne pokriva; gasi se pola sekunde pošto se prst podigne.
 * [belowTopRow] je spušta ispod gornjeg reda dugmadi kad su kontrole na ekranu, da se ne preklope.
 */
@Composable
internal fun BoxScope.PlayerLevelIndicator(levels: PlayerLevels, belowTopRow: Boolean) {
    val active = levels.active
    // Dok se pločica gasi i dalje prikazuje ono što se poslednje menjalo.
    val last = remember { arrayOf(PlayerLevel.VOLUME) }
    if (active != null) last[0] = active
    val shown = last[0]
    AnimatedVisibility(
        visible = active != null,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(durationMillis = 200, delayMillis = 500)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .safeDrawingPadding()
            .padding(top = if (belowTopRow) 76.dp else 16.dp),
    ) {
        val fraction = if (shown == PlayerLevel.VOLUME) levels.volume else levels.brightness
        val percent = (fraction * 100).roundToInt()
        val description = stringResource(
            if (shown == PlayerLevel.VOLUME) R.string.gesture_volume else R.string.gesture_brightness,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .playerPanel(RoundedCornerShape(percent = 50))
                .height(36.dp)
                .padding(horizontal = 14.dp)
                .semantics { contentDescription = "$description $percent%" },
        ) {
            Icon(
                imageVector = if (shown == PlayerLevel.VOLUME) AppIcons.VolumeUp else AppIcons.Brightness,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            // Stalna širina, da se pločica ne trese dok se broj menja.
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 34.dp),
            )
        }
    }
}

/** Koliko prst mora da pređe po sredini slike da bi se promenio kanal. */
private val ZAP_SWIPE_DISTANCE = 64.dp

/** Deo širine uz levu i desnu ivicu koji menja osvetljenje i zvuk; sredina je za kanale. */
private const val SWIPE_SIDE = 0.2f

/** Pojas uz gornju i donju ivicu koji se preskače, jer tamo sistem izvlači svoje trake. */
private val SWIPE_EDGE = 48.dp

/** Deo kraće strane ekrana koji prst pređe od najmanje do najveće vrednosti. */
private const val LEVEL_SWIPE_RANGE = 0.7f
