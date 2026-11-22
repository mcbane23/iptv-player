package com.bane23.iptvplayer.ui.mobile

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vrh ekrana (naslov i čipovi) koji se na uspravnom telefonu sklanja dok se spisak lista nadole,
 * a vraća čim se povuče nagore. Dok se vrh skuplja, spisak se ne pomera sam nego ide za prstom,
 * pa nema skoka. [canCollapse] kaže da li spisak uopšte ima kuda da se pomera.
 */
@Stable
class CollapsingHeaderState(private val canCollapse: () -> Boolean) {
    /** Koliko je vrh podignut, u pikselima: od 0 (ceo se vidi) do -[max] (skupljen u tanku traku). */
    var offset by mutableFloatStateOf(0f)
        internal set
    internal var max by mutableFloatStateOf(0f)

    /** 0 kad se vidi ceo vrh, 1 kad je skupljen. */
    val fraction: Float get() = if (max > 0f) (-offset / max).coerceIn(0f, 1f) else 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && offset == 0f && !canCollapse()) return Offset.Zero
            val moved = (offset + available.y).coerceIn(-max, 0f)
            val consumed = moved - offset
            offset = moved
            return Offset(0f, consumed)
        }
    }

    fun expand() {
        offset = 0f
    }
}

@Composable
fun rememberCollapsingHeaderState(canCollapse: () -> Boolean): CollapsingHeaderState =
    remember { CollapsingHeaderState(canCollapse) }

/**
 * [content] je ceo vrh; kad se skupi, na njegovom mestu ostaje tanka traka sa [title] i [detail]
 * (izabrana grupa i broj stavki). Dodir na traku vraća ceo vrh.
 */
@Composable
fun CollapsingHeader(
    state: CollapsingHeaderState,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val collapsed = state.fraction > 0.5f
    Layout(
        content = {
            Box(
                // Skupljen vrh se ne vidi, pa ne sme ni da hvata dodire namenjene traci.
                modifier = if (collapsed) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                } else {
                    Modifier
                },
            ) { content() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniHeight)
                    .then(
                        if (collapsed) {
                            Modifier.clickable {
                                scope.launch { animate(state.offset, 0f) { value, _ -> state.offset = value } }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
            }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val full = measurables[0].measure(loose)
        val mini = measurables[1].measure(loose)
        val max = (full.height - mini.height).coerceAtLeast(0).toFloat()
        if (state.max != max) state.max = max
        val offset = state.offset.coerceIn(-max, 0f).roundToInt()
        val fraction = if (max > 0f) -offset / max else 0f
        layout(constraints.maxWidth, full.height + offset) {
            full.placeWithLayer(0, offset) { alpha = (1f - fraction * 1.5f).coerceIn(0f, 1f) }
            mini.placeWithLayer(0, 0) { alpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f) }
        }
    }
}

private val MiniHeight = 40.dp
