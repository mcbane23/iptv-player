package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerTitle

/**
 * Kontrole plejera na telefonu, raspoređene isto kao na TV-u: gore nazad, naslov i dugmad
 * (slika u slici, zaključavanje), a sve ostalo dole: dugme za puštanje, traka i red dugmadi.
 */
@Composable
fun MobilePlayerControls(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onPip: (() -> Unit)?,
    onCast: (() -> Unit)? = null,
    bottom: @Composable ColumnScope.() -> Unit,
    chips: @Composable RowScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(AppIcons.Back, stringResource(R.string.action_back), onClose, backdrop = true)
                PlayerTitle(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f),
                )
                if (onCast != null) {
                    PlayerIconButton(AppIcons.Cast, stringResource(R.string.cast_button), onCast, backdrop = true)
                }
                if (onPip != null) {
                    PlayerIconButton(AppIcons.PictureInPicture, stringResource(R.string.player_pip), onPip, backdrop = true)
                }
                PlayerIconButton(AppIcons.Lock, stringResource(R.string.player_lock), onLock, backdrop = true)
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomStart)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bottom()
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = chips,
                )
            }
        }
    }
}

/** Dugme za puštanje i pauzu u traci, odmah posle levog dugmeta; kad puštanje ne uspe, pokušava ponovo. */
@Composable
fun PlayPauseButton(paused: Boolean, failed: Boolean, onPlayPause: () -> Unit, onRetry: () -> Unit) {
    PlayerIconButton(
        icon = if (paused || failed) AppIcons.Play else AppIcons.Pause,
        description = stringResource(
            when {
                failed -> R.string.player_retry
                paused -> R.string.player_play
                else -> R.string.player_pause
            },
        ),
        onClick = if (failed) onRetry else onPlayPause,
        primary = true,
    )
}

/** Dok je ekran zaključan vidi se samo dugme za otključavanje, i to kratko posle dodira. */
@Composable
fun BoxScope.LockedOverlay(visible: Boolean, onUnlock: () -> Unit) {
    if (!visible) return
    PlayerIconButton(
        icon = AppIcons.Lock,
        description = stringResource(R.string.player_unlock),
        onClick = onUnlock,
        primary = true,
        backdrop = true,
        size = 56.dp,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .safeDrawingPadding()
            .padding(top = 24.dp),
    )
}
