package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.guide.nowUntil
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.SkeletonLogo
import com.bane23.iptvplayer.ui.common.SkeletonTextLines
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.rememberNow

/**
 * Red sa kanalom na telefonu: broj, logo, naziv, emisija koja je sada na programu sa trakom
 * koliko je odmakla, i zvezdica za omiljene. [channel] je null dok se ta strana liste učitava.
 * Dugi pritisak otvara meni (omiljeni, vodič) ako je zadat [onGuide] ili [onToggleFavorite].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileChannelRow(
    channel: Channel?,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    showNumber: Boolean = true,
    onToggleFavorite: ((Long) -> Unit)? = null,
    onGuide: ((Channel) -> Unit)? = null,
) {
    val now by rememberNow()
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onToggleFavorite != null || onGuide != null
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = channel != null,
                    onClick = { channel?.let { onClick(it.id) } },
                    onLongClick = if (hasMenu) ({ menuOpen = true }) else null,
                )
                .padding(start = if (showNumber) 4.dp else 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showNumber) {
                Text(
                    text = channel?.number?.toString().orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .width(32.dp)
                        .padding(end = 8.dp),
                )
            }
            if (channel == null) {
                // Strana liste se još učitava: obrisi umesto praznog reda.
                SkeletonLogo()
                SkeletonTextLines(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                )
            } else {
                ChannelLogo(url = channel.logo, name = channel.name)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 4.dp),
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    channel.nowTitle?.let { nowTitle ->
                        Text(
                            text = nowUntil(nowTitle, channel.nowStopMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    channel.nowProgress(now)?.let { progress ->
                        ProgramProgress(progress = progress, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                if (onToggleFavorite != null) {
                    FavoriteButton(favorite = channel.favorite, onClick = { onToggleFavorite(channel.id) })
                }
            }
        }
        if (channel != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onToggleFavorite != null) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (channel.favorite) R.string.favorite_remove else R.string.favorite_add))
                        },
                        leadingIcon = {
                            Icon(if (channel.favorite) AppIcons.Star else AppIcons.StarOutline, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onToggleFavorite(channel.id)
                        },
                    )
                }
                if (onGuide != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.guide_title)) },
                        leadingIcon = { Icon(AppIcons.Guide, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onGuide(channel)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Kanal kao pločica u mreži sa dve kolone (Uživo na telefonu): logo levo, desno naziv, emisija
 * koja je na programu i traka koliko je odmakla. Zvezdica samo pokazuje omiljeni kanal; dugi
 * pritisak otvara meni (omiljeni, vodič). [channel] je null dok se ta strana liste učitava.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileChannelTile(
    channel: Channel?,
    onClick: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onGuide: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TileShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f), TileShape)
                .combinedClickable(
                    enabled = channel != null,
                    onClick = { channel?.let { onClick(it.id) } },
                    onLongClick = { menuOpen = true },
                )
                .heightIn(min = 58.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel == null) {
                SkeletonLogo()
                SkeletonTextLines(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
            } else {
                ChannelLogo(url = channel.logo, name = channel.name)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (channel.favorite) {
                            Icon(
                                imageVector = AppIcons.Star,
                                contentDescription = stringResource(R.string.category_favorites),
                                tint = FavoriteColor,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                    channel.nowTitle?.let { nowTitle ->
                        Text(
                            text = nowTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    channel.nowProgress(now)?.let { progress ->
                        // Traka u akcentnoj boji, ali tanka: jedina boja na pločici.
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                            )
                        }
                    }
                }
            }
        }
        if (channel != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (channel.favorite) R.string.favorite_remove else R.string.favorite_add)) },
                    leadingIcon = {
                        Icon(if (channel.favorite) AppIcons.Star else AppIcons.StarOutline, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onToggleFavorite(channel.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.guide_title)) },
                    leadingIcon = { Icon(AppIcons.Guide, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onGuide(channel)
                    },
                )
            }
        }
    }
}

private val TileShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

/** Zvezdica za dodavanje u omiljene i uklanjanje iz njih. */
@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (favorite) AppIcons.Star else AppIcons.StarOutline,
            contentDescription = description,
            tint = if (favorite) FavoriteColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Diskretna traka koliko je emisija odmakla ([progress] od 0 do 1): tanka i u prigušenoj boji,
 * da ne odvlači pažnju sa naziva kanala.
 */
@Composable
fun ProgramProgress(progress: Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color.copy(alpha = 0.15f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.55f)),
        )
    }
}

/** Zlatna zvezdica, ista na svim mestima gde se prikazuju omiljeni. */
private val FavoriteColor = androidx.compose.ui.graphics.Color(0xFFFFD166)

/** Obrisi nekoliko redova kanala, dok se lista prvi put učitava. */
@Composable
fun MobileChannelSkeletonList(modifier: Modifier = Modifier, rows: Int = 10) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        repeat(rows) { MobileChannelRow(channel = null, onClick = {}) }
    }
}
