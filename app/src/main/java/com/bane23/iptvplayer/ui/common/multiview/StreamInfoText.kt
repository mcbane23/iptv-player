package com.bane23.iptvplayer.ui.common.multiview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.VideoInfo

/** Tehnički podaci polja kao jedan red teksta, ili null dok slika nije krenula. */
@Composable
fun VideoInfo.label(): String? {
    if (decoder == null && width == 0) return null
    val kind = when {
        decoder == null -> null
        software -> stringResource(R.string.stream_info_software)
        else -> stringResource(R.string.stream_info_hardware)
    }
    val size = if (width > 0) "$width×$height" else null
    val dropped = stringResource(R.string.stream_info_dropped, droppedFrames)
    return listOfNotNull(kind, size, dropped, decoder).joinToString(" · ")
}
