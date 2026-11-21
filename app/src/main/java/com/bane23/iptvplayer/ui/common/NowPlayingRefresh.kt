package com.bane23.iptvplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.LazyPagingItems
import com.bane23.iptvplayer.domain.Channel
import kotlinx.coroutines.delay

/**
 * Drži „sada na programu” u spisku kanala tačnim dok je spisak na ekranu. Emisija uz kanal se čita
 * kad se spisak učita, pa bi bez ovoga ostala ista i kad se završi. Proverava se na početku svakog
 * minuta: spisak se čita iznova samo ako se neka od učitanih emisija završila, a inače povremeno,
 * zbog kanala koji u tom trenutku nisu imali emisiju. Mesto u spisku i fokus ostaju gde su bili.
 */
@Composable
fun KeepNowPlayingFresh(channels: LazyPagingItems<Channel>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(channels, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var refreshedAt = System.currentTimeMillis()
            while (true) {
                val before = System.currentTimeMillis()
                delay(MINUTE_MS - before % MINUTE_MS)
                val now = System.currentTimeMillis()
                val ended = channels.itemSnapshotList.items.any { channel ->
                    val stop = channel.nowStopMs
                    stop != null && stop <= now
                }
                if (ended || now - refreshedAt >= IDLE_REFRESH_MS) {
                    refreshedAt = now
                    channels.refresh()
                }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
private const val IDLE_REFRESH_MS = 10 * MINUTE_MS
