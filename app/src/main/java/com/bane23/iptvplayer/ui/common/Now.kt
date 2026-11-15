package com.bane23.iptvplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Trenutno vreme (ms) koje se osvežava svakih [stepMs]; za trake napretka emisija. */
@Composable
fun rememberNow(stepMs: Long = 30_000L): State<Long> = produceState(System.currentTimeMillis(), stepMs) {
    while (true) {
        delay(stepMs)
        value = System.currentTimeMillis()
    }
}
