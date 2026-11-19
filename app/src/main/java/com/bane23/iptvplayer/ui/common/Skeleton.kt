package com.bane23.iptvplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.bane23.iptvplayer.ui.common.theme.LocalPlaceholderColors
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Zaobljena površina na mestu sadržaja koji se još učitava, u boji teme. Obrisi su namerno
 * nepomični (bez „talasa”), da ne troše snagu slabijih TV uređaja.
 */
fun Modifier.skeleton(radius: Dp = 6.dp): Modifier = composed {
    background(LocalPlaceholderColors.current.skeleton, RoundedCornerShape(radius))
}

/** Obris logoa kanala iste veličine kao [ChannelLogo]. */
@Composable
fun SkeletonLogo(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).skeleton(size * 0.22f))
}

/** Dva obrisa teksta jedan ispod drugog: naziv i, kraći, red ispod njega. */
@Composable
fun SkeletonTextLines(modifier: Modifier = Modifier, lineHeight: Dp = 12.dp) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth(0.6f).height(lineHeight).skeleton(4.dp))
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.4f)
                .height(lineHeight * 0.8f)
                .skeleton(4.dp),
        )
    }
}
