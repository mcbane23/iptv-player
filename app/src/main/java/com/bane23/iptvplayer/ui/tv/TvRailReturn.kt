package com.bane23.iptvplayer.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Povratak iz bočnog menija strelicom Desno: sekcija ovde prijavi kako da vrati fokus tamo gde je bio pre
 * nego što je otišao u meni (Bane, 24. 11. 2026). Bez prijave meni koristi najbliži element desno.
 */
class TvRailReturn {
    internal var restore: (() -> Boolean)? = null

    fun tryRestore(): Boolean = restore?.invoke() == true
}

val LocalTvRailReturn = staticCompositionLocalOf { TvRailReturn() }

/**
 * Pamti poslednji red u levoj koloni sekcije (pretraga, grupe, kategorije) na kome je bio fokus i prijavljuje
 * ga bočnom meniju. Red se označava sa [mark] (ključ reda), a fokus mu se daje sa [focus].
 */
class TvLastRow internal constructor() {
    private var key by mutableStateOf<Any?>(null)
    internal val requester = FocusRequester()

    /** Fokus je stigao na red [rowKey]. */
    fun focused(rowKey: Any) {
        key = rowKey
    }

    /** Modifikator za red [rowKey]: dobija [FocusRequester] samo dok je on poslednji, pa korak fokusa crta dva reda. */
    @Composable
    fun mark(rowKey: Any): Modifier {
        val isLast by remember(rowKey) { derivedStateOf { key == rowKey } }
        return if (isLast) Modifier.focusRequester(requester) else Modifier
    }

    fun focus(): Boolean = key != null && requester.tryRequestFocus()
}

@Composable
fun rememberTvLastRow(): TvLastRow {
    val lastRow = remember { TvLastRow() }
    val railReturn = LocalTvRailReturn.current
    DisposableEffect(railReturn, lastRow) {
        val restore = { lastRow.focus() }
        railReturn.restore = restore
        onDispose { if (railReturn.restore === restore) railReturn.restore = null }
    }
    return lastRow
}
