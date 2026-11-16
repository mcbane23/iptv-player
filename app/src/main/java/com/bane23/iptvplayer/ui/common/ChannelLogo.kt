package com.bane23.iptvplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.ui.common.theme.OnSurfaceVariant
import com.bane23.iptvplayer.ui.common.theme.SurfaceVariant

/**
 * Logo kanala. Ako ga nema ili ne može da se učita, umesto praznog mesta stoji kratka oznaka
 * kanala iz [name] (npr. „RTS1”) na zaobljenoj podlozi iste veličine.
 */
@Composable
fun ChannelLogo(url: String?, name: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.size(size),
            )
        } else if (!name.isNullOrBlank()) {
            val initials = remember(name) { channelInitials(name) }
            Box(
                modifier = Modifier
                    .size(size)
                    .background(SurfaceVariant, RoundedCornerShape(size * 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = initials,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = TextStyle(
                        color = OnSurfaceVariant,
                        // Četiri znaka moraju da stanu u širinu, pa su duže oznake sitnije.
                        fontSize = (size.value * if (initials.length > 3) 0.26f else 0.32f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}
