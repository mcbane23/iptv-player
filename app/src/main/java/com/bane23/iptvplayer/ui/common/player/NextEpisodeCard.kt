package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.vod.NextCard
import com.bane23.iptvplayer.ui.common.vod.episodeLabel

/**
 * Kartica „Sledeća epizoda” pred kraj epizode: šta sledi, za koliko sekundi kreće i dva izbora.
 * Na telefonu su izbori dugmad. Na TV-u ([tv]) kartica ne uzima fokus, pa uz izbore piše taster
 * koji ih bira (OK i Nazad); tastere obrađuje ekran plejera.
 */
@Composable
fun NextEpisodeCard(
    card: NextCard,
    tv: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.85f))
            // Dodir na karticu ne sme da sakrije ili prikaže kontrole plejera.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (card.stillWatching) {
                stringResource(R.string.next_card_still_watching)
            } else {
                stringResource(R.string.next_card_countdown, card.secondsLeft)
            },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Text(
            text = "${episodeLabel(card.season, card.episode)} · ${card.title}",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val accept = stringResource(if (card.stillWatching) R.string.next_card_continue else R.string.next_card_play_now)
            val cancel = stringResource(R.string.action_cancel)
            CardChoice(
                label = if (tv) stringResource(R.string.next_card_key_ok, accept) else accept,
                primary = true,
                onClick = onAccept.takeIf { !tv },
            )
            CardChoice(
                label = if (tv) stringResource(R.string.next_card_key_back, cancel) else cancel,
                primary = false,
                onClick = onCancel.takeIf { !tv },
            )
        }
    }
}

@Composable
private fun CardChoice(label: String, primary: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = label,
        color = if (primary) colors.onPrimary else Color.White,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (primary) colors.primary else Color.White.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
