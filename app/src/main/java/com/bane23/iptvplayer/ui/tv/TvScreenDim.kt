package com.bane23.iptvplayer.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.prefs.ScreenDim
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.playerPanel
import kotlinx.coroutines.delay

/**
 * Zatamnjenje cele aplikacije na TV-u: crni providan sloj preko svega, [percent] posto (vidi
 * [ScreenDim]). Jedan pravougaonik bez animacije, pa ne opterećuje slabije TV-e; ne prima fokus ni
 * dodir. Kad se nivo promeni (u plejeru ili u Podešavanjima), gore u sredini kratko stoji pločica sa
 * nivoom, iznad sloja, kao pločica za osvetljenje na telefonu.
 */
@Composable
fun TvScreenDim(percent: Int) {
    var pillShown by remember { mutableStateOf(false) }
    // Nivo pri pokretanju se ne najavljuje, samo izmene.
    val last = remember { intArrayOf(percent) }
    LaunchedEffect(percent) {
        if (last[0] == percent) return@LaunchedEffect
        last[0] = percent
        pillShown = true
        delay(PILL_SHOWN_MS)
        pillShown = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (percent > ScreenDim.OFF) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = percent / 100f)))
        }
        AnimatedVisibility(
            visible = pillShown,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        ) {
            DimPill(percent)
        }
    }
}

@Composable
private fun DimPill(percent: Int) {
    val label = if (percent == ScreenDim.OFF) {
        stringResource(R.string.setting_off)
    } else {
        stringResource(R.string.screen_dim_percent, percent)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .playerPanel(RoundedCornerShape(percent = 50))
            .height(36.dp)
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = AppIcons.Brightness,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.settings_screen_dim),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        // Stalna širina, da se pločica ne trese dok se broj menja.
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 34.dp),
        )
    }
}

/** Koliko pločica sa nivoom ostaje posle poslednje izmene. */
private const val PILL_SHOWN_MS = 1500L
