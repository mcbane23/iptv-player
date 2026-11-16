package com.bane23.iptvplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.bane23.iptvplayer.ui.common.guide.formatTime
import kotlinx.coroutines.delay

/** Trenutno vreme (ms) koje se osvežava svakih [stepMs]; za trake napretka emisija. */
@Composable
fun rememberNow(stepMs: Long = 30_000L): State<Long> = produceState(System.currentTimeMillis(), stepMs) {
    while (true) {
        delay(stepMs)
        value = System.currentTimeMillis()
    }
}

/**
 * Trenutno vreme kao tekst (npr. „20:45”) za sat na ekranu. Osvežava se tačno na početku
 * svakog minuta, pa sat nikad ne kasni.
 */
@Composable
fun rememberClockText(): State<String> = produceState(formatTime(System.currentTimeMillis())) {
    while (true) {
        val now = System.currentTimeMillis()
        delay(MINUTE_MS - now % MINUTE_MS)
        value = formatTime(System.currentTimeMillis())
    }
}

private const val MINUTE_MS = 60_000L
