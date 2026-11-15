package com.bane23.iptvplayer.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Logo kanala. Ako ga nema ili ne može da se učita, ostaje prazno mesto iste veličine. */
@Composable
fun ChannelLogo(url: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier = modifier.size(size)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}
