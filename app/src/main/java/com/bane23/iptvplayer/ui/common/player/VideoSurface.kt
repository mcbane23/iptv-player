package com.bane23.iptvplayer.ui.common.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.StateFlow

/**
 * Slika plejera, bez ugrađenih kontrola (kontrole crta Compose). Titlove iz samog sadržaja prikazuje
 * PlayerView; titl iz fajla (sa interneta) stiže kao tekst u [subtitleText] i crta se na isti način
 * preko slike, pa njegovo uključivanje ne prekida gledanje.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    aspectMode: AspectMode,
    modifier: Modifier = Modifier,
    subtitleText: StateFlow<String?>? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    // Izgled titlova iz podešavanja; izmena važi odmah, i dok se nešto gleda.
    val settings by settingsViewModel.playerSettings.collectAsStateWithLifecycle()
    val fileSubtitle = subtitleText?.collectAsStateWithLifecycle()?.value
    val fileSubtitleView = remember { arrayOfNulls<SubtitleView>(1) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                // Fokus ostaje na Compose elementima, inače daljinski ne radi.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
                // Titl iz fajla dobija svoj sloj na istom mestu gde su i titlovi iz sadržaja.
                (subtitleView?.parent as? ViewGroup)?.let { frame ->
                    val extra = SubtitleView(context)
                    frame.addView(extra, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    fileSubtitleView[0] = extra
                }
            }
        },
        update = { view ->
            view.player = player
            for (subtitles in listOfNotNull(view.subtitleView, fileSubtitleView[0])) {
                subtitles.setStyle(
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        if (settings.subtitleBackground) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null,
                    ),
                )
                // Stil iz samog strima bi poništio izbor korisnika.
                subtitles.setApplyEmbeddedStyles(false)
                subtitles.setApplyEmbeddedFontSizes(false)
                subtitles.setFractionalTextSize(settings.subtitleSize.fraction)
                subtitles.setBottomPaddingFraction(settings.subtitlePosition.bottomFraction)
            }
            fileSubtitleView[0]?.setCues(
                if (fileSubtitle.isNullOrEmpty()) emptyList() else listOf(Cue.Builder().setText(fileSubtitle).build()),
            )
            view.resizeMode = when (aspectMode) {
                AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        onRelease = { view -> view.player = null },
    )
}

/**
 * Drži ekran upaljen dok se nešto pušta ili učitava. Pauziran ili neuspeo sadržaj ne drži ekran:
 * uređaj ga tada gasi po svom podešavanju, da zaboravljena pauza ne prazni bateriju.
 */
@Composable
fun KeepScreenOn(status: PlaybackStatus) {
    KeepScreenOn(
        active = status == PlaybackStatus.PLAYING || status == PlaybackStatus.LOADING || status == PlaybackStatus.RETRYING,
    )
}

/** Drži ekran upaljen dok je [active]. */
@Composable
fun KeepScreenOn(active: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}
