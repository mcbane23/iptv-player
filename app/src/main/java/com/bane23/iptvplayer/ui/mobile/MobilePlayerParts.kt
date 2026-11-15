package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerScrim
import com.bane23.iptvplayer.ui.common.player.PlayerTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Kontrole plejera na telefonu, raspoređene isto kao na TV-u: gore nazad, naslov i dugmad
 * (slika u slici, zaključavanje), u sredini veliko dugme za puštanje, dole traka i red dugmadi.
 */
@Composable
fun MobilePlayerControls(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onPip: (() -> Unit)?,
    center: @Composable RowScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    chips: @Composable RowScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            PlayerScrim(top = true, modifier = Modifier.matchParentSize())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(AppIcons.Back, stringResource(R.string.action_back), onClose)
                PlayerTitle(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                if (onPip != null) {
                    PlayerIconButton(AppIcons.PictureInPicture, stringResource(R.string.player_pip), onPip)
                }
                PlayerIconButton(AppIcons.Lock, stringResource(R.string.player_lock), onLock)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = center,
        )

        Box(modifier = Modifier.align(Alignment.BottomStart)) {
            PlayerScrim(top = false, modifier = Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 12.dp),
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

/** Dok je ekran zaključan vidi se samo dugme za otključavanje, i to kratko posle dodira. */
@Composable
fun BoxScope.LockedOverlay(visible: Boolean, onUnlock: () -> Unit) {
    if (!visible) return
    PlayerIconButton(
        icon = AppIcons.Lock,
        description = stringResource(R.string.player_unlock),
        onClick = onUnlock,
        primary = true,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .safeDrawingPadding()
            .padding(top = 24.dp),
    )
}

@HiltViewModel
class GestureHintViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    /** null dok se ne pročita iz podešavanja, da se uputstvo ne bi trepnulo. */
    val shown: StateFlow<Boolean?> = settings.gestureHintShown
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markShown() {
        viewModelScope.launch { settings.setGestureHintShown() }
    }
}

/** Uputstvo za gestove (osvetljenje i jačina zvuka), samo pri prvom otvaranju plejera. */
@Composable
fun BoxScope.FirstLaunchGestureHint(viewModel: GestureHintViewModel = hiltViewModel()) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown == false) {
            visible = true
            viewModel.markShown()
            delay(GESTURE_GUIDE_MS)
            visible = false
        }
    }
    if (!visible) return
    Text(
        text = stringResource(R.string.gesture_guide),
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 32.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

private const val GESTURE_GUIDE_MS = 5_000L
