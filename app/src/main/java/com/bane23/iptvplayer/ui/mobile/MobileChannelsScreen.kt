package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.ChannelListViewModel
import com.bane23.iptvplayer.ui.common.ChannelLogo

/** Kanali jedne kategorije na telefonu. */
@Composable
fun MobileChannelsScreen(
    playlistId: Long,
    categoryId: Long?,
    title: String,
    onPlay: (Long) -> Unit,
    onBack: () -> Unit,
    onGuide: () -> Unit,
    viewModel: ChannelListViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId, categoryId) { viewModel.select(playlistId, categoryId) }
    val channels = viewModel.channels.collectAsLazyPagingItems()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                TextButton(onClick = onGuide) { Text(stringResource(R.string.guide_title)) }
            }
            if (channels.itemCount == 0 && channels.loadState.refresh is LoadState.NotLoading) {
                Text(
                    text = stringResource(
                        if (categoryId == ChannelGroups.FAVORITES) R.string.favorites_empty else R.string.channels_empty,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                    MobileChannelRow(
                        channel = channels[index],
                        onClick = onPlay,
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }
            }
        }
    }
}

/** Red sa kanalom. [channel] je null dok se ta strana liste još učitava. */
@Composable
fun MobileChannelRow(
    channel: Channel?,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onToggleFavorite: ((Long) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = channel != null) { channel?.let { onClick(it.id) } }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(url = channel?.logo)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = channel?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel?.nowTitle?.let { nowTitle ->
                Text(
                    text = nowTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onToggleFavorite != null && channel != null) {
            FavoriteButton(favorite = channel.favorite, onClick = { onToggleFavorite(channel.id) })
        }
    }
}

/** Zvezdica za dodavanje u favorite i uklanjanje iz njih. */
@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)
    TextButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = if (favorite) "★" else "☆",
            style = MaterialTheme.typography.titleLarge,
            color = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
