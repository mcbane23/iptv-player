package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Meniji koji se otvaraju kao oblačić iznad svog dugmeta u redu ispod trake. */
enum class PlayerMenu { SUBTITLES, AUDIO, SETTINGS, SOURCES, RECORD }

/** Pamti gde je na ekranu koje dugme, da oblačić zna iznad čega da se otvori. */
class PlayerMenuAnchors {
    private val bounds = mutableStateMapOf<PlayerMenu, Rect>()

    operator fun get(menu: PlayerMenu): Rect? = bounds[menu]

    internal fun place(menu: PlayerMenu, rect: Rect) {
        if (bounds[menu] != rect) bounds[menu] = rect
    }
}

@Composable
fun rememberPlayerMenuAnchors(): PlayerMenuAnchors = remember { PlayerMenuAnchors() }

/** Dugme koje otvara [menu]: njegovo mesto se pamti u [anchors]. */
fun Modifier.playerMenuAnchor(anchors: PlayerMenuAnchors, menu: PlayerMenu): Modifier =
    onGloballyPositioned { anchors.place(menu, it.boundsInRoot()) }

/**
 * Meni plejera kao mali prozor („oblačić”) tačno iznad dugmeta koje ga otvara ([anchor], mesto
 * dugmeta na ekranu). Širok je [width], a visok koliko ima stavki, najviše do vrha ekrana; duži
 * sadržaj se pomera unutar njega (to rešava [content]). Kad dugme nije na ekranu, oblačić stoji u
 * donjem levom uglu. Zove se iz korenskog Box-a plejera, koji pokriva ceo ekran. Dodir na sam
 * oblačić ne stiže do plejera, pa ga ne zatvara.
 */
@Composable
fun BoxScope.PlayerMenuBubble(
    anchor: Rect?,
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Mesto dugmeta je mereno od ugla prozora; ovde se prevodi u mere ovog sloja.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val surface = MaterialTheme.colorScheme.surface
    Layout(
        content = {
            // Repić koji pokazuje na dugme; crta se ispod oblačića, pa viri samo donja polovina.
            Box(
                modifier = Modifier
                    .size(TailSize)
                    .rotate(45f)
                    .background(surface),
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .clip(BubbleShape)
                    .background(surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 6.dp),
            ) { content() }
        },
        modifier = modifier
            .matchParentSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val anchorTop = anchor?.let { (it.top - origin.y).roundToInt() } ?: (constraints.maxHeight - 64.dp.roundToPx())
        val anchorLeft = anchor?.let { (it.left - origin.x).roundToInt() } ?: margin
        val tail = measurables[0].measure(Constraints())
        val body = measurables[1].measure(
            Constraints(
                maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0),
                maxHeight = (anchorTop - gap - margin).coerceAtLeast(0),
            ),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = anchorLeft.coerceIn(margin, (constraints.maxWidth - body.width - margin).coerceAtLeast(margin))
            val bottom = anchorTop - gap
            if (anchor != null) {
                val center = (anchor.center.x - origin.x).roundToInt()
                val tailX = (center - tail.width / 2).coerceIn(x + tail.width, (x + body.width - 2 * tail.width).coerceAtLeast(x + tail.width))
                tail.place(tailX, bottom - tail.height / 2)
            }
            body.place(x, bottom - body.height)
        }
    }
}

private val BubbleShape = RoundedCornerShape(14.dp)
private val TailSize = 12.dp
