package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.SearchAllViewModel
import com.bane23.iptvplayer.ui.common.guide.formatDayTime
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices

/**
 * Jedna pretraga za sve na telefonu: kanali, emisije iz vodiča, filmovi i serije, po grupama.
 * Emisija koja traje pušta kanal; buduća dobija podsetnik.
 */
@Composable
fun MobileSearchAllScreen(
    onPlay: (Long) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    viewModel: SearchAllViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.notice)
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val now = System.currentTimeMillis()
    val showRecent = state.query.isBlank() && recent.isNotEmpty()
    val play: (Long) -> Unit = { id ->
        viewModel.remember()
        onPlay(id)
    }
    val openVod: (VodItem, Boolean) -> Unit = { item, series ->
        viewModel.remember()
        onOpenVod(item, series)
    }

    // Providno: pozadinu sa sjajem crta početni ekran.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_all_hint)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.remember()
                    keyboard?.hide()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when {
                state.nothingFound -> EmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_empty_vod))
                state.searched.isEmpty() && !state.searching && !showRecent ->
                    EmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_all_intro))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (showRecent) {
                    item(key = "h-recent") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { GroupTitle(stringResource(R.string.search_recent), recent.size) }
                            TextButton(onClick = viewModel::clearRecent, modifier = Modifier.padding(end = 8.dp)) {
                                Text(stringResource(R.string.search_recent_clear))
                            }
                        }
                    }
                    items(recent, key = { "q$it" }) { query ->
                        ResultRow(title = query, onClick = {
                            keyboard?.hide()
                            viewModel.submit(query)
                        }) { GroupIcon(AppIcons.History) }
                    }
                }
                if (state.channels.isNotEmpty()) {
                    item(key = "h-channels") { GroupTitle(stringResource(R.string.search_group_channels), state.channels.size) }
                    items(state.channels, key = { "c${it.id}" }) { channel ->
                        ResultRow(title = channel.name, onClick = { play(channel.id) }) {
                            ChannelLogo(url = channel.logo, name = channel.name, size = 36.dp)
                        }
                    }
                }
                if (state.programs.isNotEmpty()) {
                    item(key = "h-programs") { GroupTitle(stringResource(R.string.search_group_programs), state.programs.size) }
                    items(state.programs, key = { "p${it.channelId}-${it.startMs}" }) { hit ->
                        val onAir = hit.startMs <= now
                        val reminded = hit.key in reminders
                        ResultRow(
                            title = if (reminded) "🔔 ${hit.title}" else hit.title,
                            subtitle = listOf(
                                hit.channelName,
                                if (onAir) stringResource(R.string.search_on_air, formatTime(hit.stopMs)) else formatDayTime(hit.startMs),
                            ).joinToString(" · "),
                            trailing = when {
                                onAir -> stringResource(R.string.guide_watch)
                                reminded -> stringResource(R.string.reminder_remove)
                                else -> stringResource(R.string.reminder_add)
                            },
                            onClick = { if (onAir) play(hit.channelId) else viewModel.toggleReminder(hit) },
                        ) { GroupIcon(AppIcons.Guide) }
                    }
                }
                // Filmovi i serije kao red postera, kao u svojim karticama.
                if (state.movies.isNotEmpty()) {
                    item(key = "h-movies") { GroupTitle(stringResource(R.string.vod_movies), state.movies.size) }
                    item(key = "r-movies") { PosterRow(state.movies) { openVod(it, false) } }
                }
                if (state.series.isNotEmpty()) {
                    item(key = "h-series") { GroupTitle(stringResource(R.string.vod_series), state.series.size) }
                    item(key = "r-series") { PosterRow(state.series) { openVod(it, true) } }
                }
            }
        }
    }
}

/** Naslov grupe sa brojem pogodaka, kao naslov nad kanalima u Uživo. */
@Composable
private fun GroupTitle(text: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 10.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Red postera za filmove ili serije u rezultatima. */
@Composable
private fun PosterRow(list: List<VodItem>, onOpen: (VodItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(list, key = { it.id }) { item ->
            PosterTile(
                name = item.name,
                poster = item.poster,
                onClick = { onOpen(item) },
                modifier = Modifier.width(104.dp),
            )
        }
    }
}

@Composable
private fun GroupIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
}

@Composable
private fun ResultRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: String? = null,
    leading: @Composable () -> Unit,
) {
    // Pločica kao red kanala u Uživo.
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
