package com.bane23.iptvplayer.ui.common.player

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ChannelSource
import kotlinx.coroutines.flow.Flow

/** Kratka poruka kad plejer sam pređe na izvor kanala iz druge liste, jer se prethodni nije javio. */
@Composable
fun SourceNotices(notices: Flow<String>) {
    val context = LocalContext.current
    LaunchedEffect(notices) {
        notices.collect { playlist ->
            Toast.makeText(context, context.getString(R.string.player_source_switched, playlist), Toast.LENGTH_LONG).show()
        }
    }
}

/** Naziv izvora za panel „Izvori”: naziv liste i, po potrebi, da se gleda sada ili da se nije javio. */
@Composable
fun ChannelSource.label(ui: PlayerUiState): String = when {
    channelId == ui.sourceId -> stringResource(R.string.player_source_current, playlistName)
    channelId in ui.failedSources -> stringResource(R.string.player_source_failed, playlistName)
    else -> playlistName
}
