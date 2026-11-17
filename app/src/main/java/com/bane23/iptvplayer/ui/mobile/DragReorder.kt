package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Prevlačenje reda u `LazyColumn` na novo mesto. Spisak sme da sadrži samo redove koji se prevlače,
 * da mesto u spisku bude i mesto u podacima. [onMove] premešta stavku u podacima (sa mesta, na mesto)
 * čim prevučeni red pređe preko susednog; [onDrop] javlja da je prst podignut.
 */
@Stable
class DragReorderState internal constructor(
    val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Ključ reda koji se upravo prevlači, ili null. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /** Gornja ivica prevučenog reda u koordinatama spiska; prati prst, ne mesto u spisku. */
    private var draggedTop by mutableFloatStateOf(0f)
    private var draggedIndex = -1

    private fun dragged(): LazyListItemInfo? {
        val key = draggedKey ?: return null
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    }

    fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggedKey = key
        draggedIndex = item.index
        draggedTop = item.offset.toFloat()
    }

    fun dragBy(delta: Float) {
        val item = dragged() ?: return
        val info = listState.layoutInfo
        // Red sme najviše do pola da izađe iz vidnog dela, da ostane pod prstom.
        val half = item.size / 2f
        draggedTop = (draggedTop + delta).coerceIn(info.viewportStartOffset - half, info.viewportEndOffset - half)
        settle()
    }

    fun end() {
        if (draggedKey == null) return
        draggedKey = null
        draggedIndex = -1
        onDrop()
    }

    /** Koliko je prevučeni red pomeren od mesta na kom ga spisak trenutno drži. */
    fun offsetOf(key: Any): Float {
        if (key != draggedKey) return 0f
        val item = dragged() ?: return 0f
        return draggedTop - item.offset
    }

    /** Ako je sredina prevučenog reda prešla na susedni red, menjaju mesta. */
    private fun settle() {
        val item = dragged() ?: return
        // Raspored još nije stigao posle prethodne zamene; sačekati sledeći kadar.
        if (item.index != draggedIndex) return
        val middle = draggedTop + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != item.key && middle >= it.offset && middle <= it.offset + it.size
        } ?: return
        // Spisak se drži za ključ prvog vidljivog reda; kad baš on menja mesto, spisak bi skočio za njim.
        val first = listState.firstVisibleItemIndex
        if (item.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        onMove(item.index, target.index)
        draggedIndex = target.index
    }

    /** Jedan kadar: kad je red na ivici, spisak se sam pomera pod njim. */
    internal fun frame() {
        val item = dragged() ?: return
        val info = listState.layoutInfo
        val over = when {
            draggedTop < info.viewportStartOffset -> draggedTop - info.viewportStartOffset
            draggedTop + item.size > info.viewportEndOffset -> draggedTop + item.size - info.viewportEndOffset
            else -> 0f
        }
        if (over != 0f) listState.dispatchRawDelta((over * EDGE_SPEED).coerceIn(-EDGE_MAX_STEP, EDGE_MAX_STEP))
        settle()
    }

    private companion object {
        const val EDGE_SPEED = 0.35f
        const val EDGE_MAX_STEP = 48f
    }
}

@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): DragReorderState {
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val state = remember(listState) { DragReorderState(listState, { from, to -> move(from, to) }, { drop() }) }
    val dragging = state.draggedKey != null
    LaunchedEffect(state, dragging) {
        if (dragging) while (true) withFrameNanos { state.frame() }
    }
    return state
}

/** Ručka reda: prevlačenje počinje čim prst krene, bez dugog pritiska. */
fun Modifier.dragHandle(state: DragReorderState, key: Any, onStart: () -> Unit = {}): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = {
                state.start(key)
                onStart()
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, amount ->
                change.consume()
                state.dragBy(amount.y)
            },
        )
    }
