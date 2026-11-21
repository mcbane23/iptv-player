package com.bane23.iptvplayer.ui.common.player

import androidx.compose.runtime.Composable
import com.bane23.iptvplayer.ui.common.AppMessages
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ChannelSource
import kotlinx.coroutines.flow.Flow

/** Kratka poruka kad plejer sam pređe na izvor kanala iz druge liste, jer se prethodni nije javio. */
@Composable
fun SourceNotices(notices: Flow<String>) {
    val resources = LocalResources.current
    LaunchedEffect(notices, resources) {
        notices.collect { playlist ->
            AppMessages.show(resources.getString(R.string.player_source_switched, playlist), long = true)
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
