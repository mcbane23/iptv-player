package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Meniji koji se otvaraju kao oblačić iznad vremenske trake, poravnati sa svojim dugmetom. */
enum class PlayerMenu { SUBTITLES, AUDIO, SETTINGS, SOURCES, RECORD }

/** Slova u menijima plejera: ista kao u dugmadima ispod trake. */
val PlayerMenuText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

/** Sitniji tekst u menijima plejera: napomene i drugi red stavke. */
val PlayerMenuNote = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)

/** Manje važan tekst i znaci u menijima plejera. */
val PlayerMenuMuted = Color.White.copy(alpha = 0.7f)

/** Linija koja deli delove menija plejera. */
val PlayerMenuDivider = Color.White.copy(alpha = 0.16f)

/**
 * Pamti gde je na ekranu koje dugme i gde je vremenska traka, da oblačić zna gde da se otvori:
 * poravnat sa svojim dugmetom, a iznad trake, da je ne prekrije.
 */
class PlayerMenuAnchors {
    private val bounds = mutableStateMapOf<PlayerMenu, Rect>()
    private var bar by mutableStateOf<Rect?>(null)

    /**
     * Vrh vremenske trake (iznad nje nema drugih donjih kontrola) u koordinatama prozora; null dok
     * se ne izmeri. Važi samo dok su kontrole na ekranu: posle sklanjanja ostaje poslednja vrednost.
     */
    var controlsTopInWindow by mutableStateOf<Float?>(null)
        private set

    /** Mesto iznad kog se otvara [menu]: leva ivica dugmeta, a vrh trake (ili dugmeta, ako trake nema). */
    operator fun get(menu: PlayerMenu): Rect? {
        val chip = bounds[menu] ?: return null
        val top = bar?.top ?: return chip
        return if (top < chip.top) chip.copy(top = top) else chip
    }

    internal fun place(menu: PlayerMenu, rect: Rect) {
        if (bounds[menu] != rect) bounds[menu] = rect
    }

    internal fun placeBar(rect: Rect, windowTop: Float) {
        if (bar != rect) bar = rect
        if (controlsTopInWindow != windowTop) controlsTopInWindow = windowTop
    }
}

@Composable
fun rememberPlayerMenuAnchors(): PlayerMenuAnchors = remember { PlayerMenuAnchors() }

/** Dugme koje otvara [menu]: njegovo mesto se pamti u [anchors]. */
fun Modifier.playerMenuAnchor(anchors: PlayerMenuAnchors, menu: PlayerMenu): Modifier =
    onGloballyPositioned { anchors.place(menu, it.boundsInRoot()) }

/** Vremenska traka plejera: meniji se otvaraju iznad nje. */
fun Modifier.playerMenuBar(anchors: PlayerMenuAnchors): Modifier =
    onGloballyPositioned { anchors.placeBar(it.boundsInRoot(), it.boundsInWindow().top) }

/**
 * Meni plejera kao mala pločica („oblačić”) iznad vremenske trake, poravnata sa dugmetom koje je
 * otvara ([anchor], iz [PlayerMenuAnchors]). Izgleda kao ostale pločice plejera: tamna, poluprovidna,
 * sa tankim okvirom. Široka je [width], a visoka koliko ima stavki, najviše do vrha ekrana; duži
 * sadržaj se pomera unutar nje (to rešava [content]). Kad dugme nije na ekranu, oblačić stoji u
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
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .width(width)
                    .playerPanel(BubbleShape)
                    .clip(BubbleShape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(4.dp),
            ) { content() }
        },
        modifier = modifier
            .matchParentSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val anchorTop = anchor?.let { (it.top - origin.y).roundToInt() } ?: (constraints.maxHeight - 64.dp.roundToPx())
        val anchorLeft = anchor?.let { (it.left - origin.x).roundToInt() } ?: margin
        val body = measurables[0].measure(
            Constraints(
                maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0),
                maxHeight = (anchorTop - gap - margin).coerceAtLeast(0),
            ),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = anchorLeft.coerceIn(margin, (constraints.maxWidth - body.width - margin).coerceAtLeast(margin))
            body.place(x, anchorTop - gap - body.height)
        }
    }
}

private val BubbleShape = RoundedCornerShape(10.dp)
