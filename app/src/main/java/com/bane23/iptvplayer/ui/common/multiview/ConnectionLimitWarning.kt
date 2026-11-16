package com.bane23.iptvplayer.ui.common.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bane23.iptvplayer.R

/** Upozorenje da je pušteno više kanala nego što nalog kod provajdera dozvoljava. Ništa ne zabranjuje. */
@Composable
fun ConnectionLimitWarning(limit: Int, modifier: Modifier = Modifier) {
    BasicText(
        text = pluralStringResource(R.plurals.multiview_connection_limit, limit, limit),
        style = TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(12.dp)
            .background(Color(0xE6B3261E), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
