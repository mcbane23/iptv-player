package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.SearchAllViewModel
import com.bane23.iptvplayer.ui.common.guide.formatDayTime
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.theme.glowBackground

/**
 * Jedna pretraga za sve na TV-u: polje za unos gore, ispod rezultati po grupama (kanali, emisije
 * iz vodiča, filmovi, serije). OK na emisiji koja traje pušta kanal; na budućoj uključuje podsetnik.
 */
@Composable
fun TvSearchAllScreen(
    onPlay: (Long) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    viewModel: SearchAllViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.notice)
    val fieldFocus = remember { FocusRequester() }
    val now = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.action_search),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.search_all_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .focusRequester(fieldFocus),
        )
        when {
            state.nothingFound -> TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_empty_vod))
            state.searching && state.channels.isEmpty() ->
                TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_all_searching))
            state.searched.isEmpty() -> TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_all_intro))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.channels.isNotEmpty()) {
                item(key = "h-channels") { GroupTitle(stringResource(R.string.search_group_channels)) }
                items(state.channels, key = { "c${it.id}" }) { channel ->
                    TvListRow(onClick = { onPlay(channel.id) }) { color ->
                        ChannelLogo(url = channel.logo, name = channel.name, size = 36.dp)
                        RowText(channel.name, color, Modifier.weight(1f))
                    }
                }
            }
            if (state.programs.isNotEmpty()) {
                item(key = "h-programs") { GroupTitle(stringResource(R.string.search_group_programs)) }
                items(state.programs, key = { "p${it.channelId}-${it.startMs}" }) { hit ->
                    val onAir = hit.startMs <= now
                    val reminded = hit.key in reminders
                    TvListRow(onClick = { if (onAir) onPlay(hit.channelId) else viewModel.toggleReminder(hit) }) { color ->
                        RowIcon(AppIcons.Guide, color)
                        RowText(if (reminded) "🔔 ${hit.title}" else hit.title, color, Modifier.weight(1f))
                        Text(
                            text = listOf(
                                hit.channelName,
                                if (onAir) stringResource(R.string.search_on_air, formatTime(hit.stopMs)) else formatDayTime(hit.startMs),
                                when {
                                    onAir -> stringResource(R.string.guide_watch)
                                    reminded -> stringResource(R.string.reminder_remove)
                                    else -> stringResource(R.string.reminder_add)
                                },
                            ).joinToString(" · "),
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                    }
                }
            }
            if (state.movies.isNotEmpty()) {
                item(key = "h-movies") { GroupTitle(stringResource(R.string.vod_movies)) }
                items(state.movies, key = { "m${it.id}" }) { item ->
                    TvListRow(onClick = { onOpenVod(item, false) }) { color ->
                        RowIcon(AppIcons.Movies, color)
                        RowText(item.name, color, Modifier.weight(1f))
                    }
                }
            }
            if (state.series.isNotEmpty()) {
                item(key = "h-series") { GroupTitle(stringResource(R.string.vod_series)) }
                items(state.series, key = { "s${it.id}" }) { item ->
                    TvListRow(onClick = { onOpenVod(item, true) }) { color ->
                        RowIcon(AppIcons.Series, color)
                        RowText(item.name, color, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun RowIcon(icon: ImageVector, color: Color) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
}

@Composable
private fun RowText(text: String, color: Color, modifier: Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 16.dp),
    )
}
