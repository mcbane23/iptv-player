package com.bane23.iptvplayer.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Beleži da je korisnik nešto pritisnuo ili dodirnuo. Po tome čuvar ekrana zna koliko dugo
 * niko ništa nije dirao.
 */
object UserActivity {
    private val counter = MutableStateFlow(0L)

    /** Menja se na svaki taster ili dodir; sama vrednost ne znači ništa. */
    val ticks: StateFlow<Long> = counter

    fun touch() {
        counter.update { it + 1 }
    }
}

/**
 * Za sadržaj prozora (Dialog): tasteri u njemu ne prolaze kroz aktivnost, pa se ovde beleže
 * da se čuvar ekrana ne upali dok se korisnik kreće po prozoru.
 */
fun Modifier.userActivity(): Modifier = onPreviewKeyEvent {
    UserActivity.touch()
    false
}
