package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * Mirno pomeranje spiska za fokusom. Na TV-u Compose podrazumevano drži fokusiran red na istom
 * mestu (oko trećine visine), pa se na svaki pritisak D-pada pomera ceo spisak i tekst „skače”.
 * Unutar [content] se umesto toga pomera samo fokus, a spisak tek kad fokus priđe ivici. Važi za
 * sve liste, mreže i prozore unutar [content], pa se postavlja jednom, oko celog TV interfejsa.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvEdgeScroll(content: @Composable () -> Unit) {
    val margin = with(LocalDensity.current) { EDGE_MARGIN.toPx() }
    val spec = remember(margin) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                edgeScrollDistance(offset, size, containerSize, margin)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Koliko treba pomeriti spisak visine [containerSize] da element na mestu [offset], visine [size],
 * bude ceo na ekranu i da iza njega ostane [margin] mesta (da se vidi i sledeći red). Nula znači
 * da se spisak ne pomera. Sve je u pikselima; negativno je pomeranje ka početku.
 */
internal fun edgeScrollDistance(offset: Float, size: Float, containerSize: Float, margin: Float): Float {
    // Kad element sa razmakom ne staje, razmak se smanjuje, da zahtev uvek može da se ispuni.
    val keep = min(margin, (containerSize - size) / 2f).coerceAtLeast(0f)
    val leading = offset - keep
    val trailing = offset + size + keep
    return when {
        leading >= 0f && trailing <= containerSize -> 0f
        // Element viši od spiska koji ga već ispunjava.
        leading < 0f && trailing > containerSize -> 0f
        abs(leading) < abs(trailing - containerSize) -> leading
        else -> trailing - containerSize
    }
}

/** Jedan red spiska: redni broj, mesto u odnosu na početak vidljivog dela i visina, u pikselima. */
internal data class RowSpan(val index: Int, val offset: Int, val size: Int)

/**
 * Koliko treba odmah pomeriti spisak da red [target] (onaj na koji fokus upravo prelazi) bude ceo na
 * ekranu, sa [margin] mesta iza njega. [visible] su redovi koji su sada na ekranu, [spacing] razmak među
 * redovima, [viewport] visina spiska. Red koji još nije na ekranu se procenjuje po visini najbližeg
 * vidljivog. Nula znači da pomeranje ne treba.
 */
internal fun heldKeyScrollDelta(target: Int, visible: List<RowSpan>, spacing: Int, viewport: Int, margin: Float): Float {
    val first = visible.firstOrNull() ?: return 0f
    val last = visible.last()
    val row = visible.firstOrNull { it.index == target }
    val (offset, size) = when {
        row != null -> row.offset to row.size
        target > last.index -> last.offset + (target - last.index) * (last.size + spacing) to last.size
        else -> first.offset - (first.index - target) * (first.size + spacing) to first.size
    }
    return edgeScrollDistance(offset.toFloat(), size.toFloat(), viewport.toFloat(), margin)
}

/**
 * Glatko listanje dok se taster Gore ili Dole drži. Compose spisak pomera animacijom, a fokus pri držanju
 * tastera ide brže od nje. Kad fokus pobegne ispred vidljivog dela, Compose za svaki korak iznova pravi
 * sve redove između ivice ekrana i fokusa, pa posle desetak redova spisak stane dok animacija ne stigne
 * fokus (primetno kod težih redova, sa logom i programom). Zato se pri ponovljenom pritisku spisak pomera
 * odmah, pre nego što fokus pređe na sledeći red, i fokus nikad ne izlazi sa ekrana. Pojedinačan pritisak
 * i dalje ide uobičajenom animacijom.
 *
 * [focusedIndex] je redni broj reda na kome je fokus (ili -1), [marginPx] isti razmak kao u [TvEdgeScroll].
 */
fun Modifier.tvHeldKeyScroll(state: LazyListState, marginPx: Float, focusedIndex: () -> Int): Modifier =
    onPreviewKeyEvent { event ->
        val step = when (event.key) {
            Key.DirectionDown -> 1
            Key.DirectionUp -> -1
            else -> 0
        }
        val focused = focusedIndex()
        if (step != 0 && focused >= 0 && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0) {
            val info = state.layoutInfo
            val target = focused + step
            if (target in 0 until info.totalItemsCount) {
                val delta = heldKeyScrollDelta(
                    target = target,
                    visible = info.visibleItemsInfo.map { RowSpan(it.index, it.offset - info.viewportStartOffset, it.size) },
                    spacing = info.mainAxisItemSpacing,
                    viewport = info.viewportEndOffset - info.viewportStartOffset,
                    margin = marginPx,
                )
                if (delta != 0f) state.dispatchRawDelta(delta)
            }
        }
        // Taster se ne troši: fokus posle ovoga prelazi na sledeći red kao i inače.
        false
    }

/** Nešto više od visine jednog reda, da se iza fokusa vidi sledeći red ili naslov odeljka. */
internal val EDGE_MARGIN = 56.dp

/**
 * Spisak se ne pomera sam za fokusom. Za spiskove koji se pomeraju red po red (Početna, mreža postera):
 * ekran sam dovodi red sa fokusom na vrh, pa gornji red nikad nije isečen (Bane, 23. 11. 2026).
 */
@OptIn(ExperimentalFoundationApi::class)
internal val NoFocusScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
