package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlayerSettings

/**
 * Umanjena slika sa probnim titlom: pokazuje izabranu veličinu slova, položaj i pozadinu
 * onako kako će izgledati u plejeru (isti udeli visine slike kao u [VideoSurface]).
 */
@Composable
fun SubtitlePreview(settings: PlayerSettings, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            // Nije crno, da se vidi razlika između titla sa pozadinom i bez nje.
            .background(Brush.verticalGradient(listOf(Color(0xFF4A5A6A), Color(0xFF26303A)))),
    ) {
        val density = LocalDensity.current
        val fontSize = with(density) { (maxHeight * settings.subtitleSize.fraction).toSp() }
        val style = TextStyle(color = Color.White, fontSize = fontSize, textAlign = TextAlign.Center)
        val text = stringResource(R.string.subtitle_preview_text)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * settings.subtitlePosition.bottomFraction)
                .padding(horizontal = 8.dp)
                .background(if (settings.subtitleBackground) Color.Black else Color.Transparent)
                .padding(horizontal = 4.dp),
        ) {
            // Crna ivica oko slova, kao u plejeru.
            BasicText(
                text = text,
                style = style.copy(
                    color = Color.Black,
                    drawStyle = Stroke(width = with(density) { fontSize.toPx() } / 8f, join = StrokeJoin.Round),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(text = text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
