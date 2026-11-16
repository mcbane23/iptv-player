package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.player.VideoInfo
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.multiview.label

/**
 * Tehnički podaci o slici (dekoder, rezolucija, ispuštene slike) u uglu plejera. Prikazuje se samo
 * kad je uključeno u podešavanjima; služi da se otkrije zašto slika secka ili koči.
 */
@Composable
fun StreamDebugLabel(video: VideoInfo, modifier: Modifier = Modifier, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val settings by settingsViewModel.playerSettings.collectAsStateWithLifecycle()
    if (!settings.showStreamInfo) return
    val text = video.label() ?: return
    BasicText(
        text = text,
        style = TextStyle(color = Color.White, fontSize = 12.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
