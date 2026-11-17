package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * Mirno pomeranje spiska za fokusom. Na TV-u Compose podrazumevano drži fokusiran red na istom
 * mestu (oko trećine visine), pa se na svaki pritisak D-pada pomera ceo spisak i tekst „skače”.
 * Unutar [content] se umesto toga pomera samo fokus, a spisak tek kad fokus priđe ivici.
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

/** Nešto više od visine jednog reda, da se iza fokusa vidi sledeći red ili naslov odeljka. */
private val EDGE_MARGIN = 56.dp
