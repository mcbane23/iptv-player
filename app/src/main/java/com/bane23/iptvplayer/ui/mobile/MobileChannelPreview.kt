package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.ChannelPreviewViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.timeRangeLeft
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.rememberNow

/**
 * Desna kolona Uživo na položenom telefonu, po uzoru na TV: slika izabranog kanala (bez zvuka), šta
 * je na njemu sada i šta sledi, i dugme koje ga otvara preko celog ekrana. Dok nijedan kanal nije
 * izabran ([selected] je false), stoji kratko uputstvo. [channel] može da izostane i kad je kanal
 * izabran (njegov red još nije učitan); tada nema samo loga i naziva.
 */
@Composable
fun MobileChannelPreviewPane(
    viewModel: ChannelPreviewViewModel,
    selected: Boolean,
    channel: Channel?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!selected) {
        EmptyState(icon = AppIcons.LiveTv, text = stringResource(R.string.live_preview_hint), modifier = modifier)
        return
    }
    val player by viewModel.player.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val later by viewModel.later.collectAsStateWithLifecycle()
    val now by rememberNow()
    val colors = MaterialTheme.colorScheme
    val openLabel = stringResource(R.string.live_preview_open)
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            val current = player
            if (current != null) {
                VideoSurface(player = current, aspectMode = AspectMode.FIT, modifier = Modifier.fillMaxSize())
                Text(
                    text = stringResource(R.string.live_badge),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(LiveRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                statusMessage(sourceMissing = false, playback = playback)?.let { status ->
                    Text(
                        text = status.lineSequence().first(),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (channel != null) {
                // Slike nema (kanal zaključan, pregled isključen ili se tek pokreće): logo kanala.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    ChannelLogo(url = channel.logo, name = channel.name, size = 56.dp)
                }
            }
        }
        if (channel != null) {
            Text(
                text = listOfNotNull(channel.number?.toString(), channel.name).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        nowNext.now?.let { program ->
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = program.timeRangeLeft(now),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Program za naredne sate: vreme i naziv, po jedan red.
        Column(modifier = Modifier.padding(top = 6.dp)) {
            later.forEach { next ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.07f)),
                )
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = formatTime(next.startMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onBackground,
                        maxLines = 1,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        text = next.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = openLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Oznaka „uživo” na slici pregleda. */
private val LiveRed = Color(0xFFE5484D)
