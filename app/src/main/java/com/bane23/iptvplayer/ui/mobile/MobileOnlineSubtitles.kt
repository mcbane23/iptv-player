package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.PlayerMenuDivider
import com.bane23.iptvplayer.ui.common.player.PlayerMenuMuted
import com.bane23.iptvplayer.ui.common.player.PlayerMenuNote
import com.bane23.iptvplayer.ui.common.player.PlayerMenuText
import com.bane23.iptvplayer.ui.common.subtitles.appliedSubtitleToast
import com.bane23.iptvplayer.ui.common.subtitles.details
import com.bane23.iptvplayer.ui.common.subtitles.message
import com.bane23.iptvplayer.ui.common.vod.OnlineSubtitlesState

/** Širina oblačića sa rezultatima; širi je od spiska titlova jer su nazivi dugi. */
internal val MobileOnlineMenuWidth = 320.dp

/**
 * Titlovi nađeni na internetu za ono što se gleda: drugi korak u oblačiću „Titlovi”
 * (PlayerMenuBubble). Prvi red vraća na spisak titlova ([onBack]). Dodir na rezultat preuzima titl i
 * uključuje ga, posle čega se meni sam zatvara ([onDone]).
 */
@Composable
internal fun MobileOnlineSubtitlesMenu(
    state: OnlineSubtitlesState,
    onPick: (SubtitleResult) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(state.appliedId) {
        if (state.appliedId != null) {
            appliedSubtitleToast(context, state.remaining)
            onDone()
        }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MobileMenuRowHeight)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 6.dp),
        ) {
            Icon(
                imageVector = AppIcons.ChevronLeft,
                contentDescription = stringResource(R.string.action_back),
                tint = PlayerMenuMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.player_subtitles),
                color = Color.White,
                style = PlayerMenuText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        HorizontalDivider(color = PlayerMenuDivider, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        val results = state.results.orEmpty()
        val status = when {
            state.searching -> stringResource(R.string.subtitles_searching)
            state.failure != null -> stringResource(state.failure.message())
            state.results != null && results.isEmpty() -> stringResource(R.string.subtitles_none_found)
            else -> null
        }
        if (status != null) {
            Text(
                text = status,
                color = if (state.failure != null) MaterialTheme.colorScheme.error else PlayerMenuMuted,
                style = PlayerMenuText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
        results.forEach { result ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.downloadingId == null, role = Role.Button) { onPick(result) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = result.name,
                    color = Color.White,
                    style = PlayerMenuText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.downloadingId == result.fileId) {
                        stringResource(R.string.subtitles_downloading)
                    } else {
                        result.details()
                    },
                    color = PlayerMenuMuted,
                    style = PlayerMenuNote,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
