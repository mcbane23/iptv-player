package com.bane23.iptvplayer.ui.common.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.VideoInfo
import com.bane23.iptvplayer.ui.common.multiview.label

/**
 * Informacije o strimu preko slike (rezolucija, kodek, protok, zvuk) i tehnički podaci [technical]
 * (dekoder, ispuštene slike, prekidi), koji pomažu da se otkrije zašto slika secka ili koči.
 * Otvara ih i zatvara dugme „Informacije”; ostaju vidljive i kad se kontrole sklone.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerStreamInfo(player: Player, technical: VideoInfo, modifier: Modifier = Modifier) {
    // Menja se kad plejer javi nove trake ili novu veličinu slike, pa se tekst ponovo računa.
    var version by remember(player) { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                version++
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                version++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val (video, audio) = remember(player, version) {
        val videoFormat = player.selectedFormat(C.TRACK_TYPE_VIDEO)
        val audioFormat = player.selectedFormat(C.TRACK_TYPE_AUDIO)
        val size = player.videoSize
        val videoLine = videoInfoLine(
            width = if (size.width > 0) size.width else videoFormat?.width ?: 0,
            height = if (size.height > 0) size.height else videoFormat?.height ?: 0,
            frameRate = videoFormat?.frameRate ?: 0f,
            mimeType = videoFormat?.sampleMimeType,
            bitrate = videoFormat?.bitrate ?: 0,
        )
        val audioLine = audioFormat?.let {
            audioInfoLine(it.sampleMimeType, it.channelCount, it.sampleRate, it.bitrate)
        }
        videoLine to audioLine
    }

    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .playerPanel(RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (video == null && audio == null) {
            InfoLine(null, stringResource(R.string.stream_info_unknown))
        } else {
            video?.let { InfoLine(stringResource(R.string.stream_info_video), it) }
            audio?.let { InfoLine(stringResource(R.string.player_audio), it) }
        }
        technical.label()?.let { InfoLine(stringResource(R.string.stream_info_technical), it, maxLines = 4) }
    }
}

@Composable
private fun InfoLine(label: String?, value: String, maxLines: Int = 2) {
    if (label != null) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
    Text(
        text = value,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Format trake koja se trenutno pušta, za dati tip (video ili zvuk). */
private fun Player.selectedFormat(trackType: Int): Format? {
    for (group in currentTracks.groups) {
        if (group.type != trackType) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) return group.getTrackFormat(index)
        }
    }
    return null
}
