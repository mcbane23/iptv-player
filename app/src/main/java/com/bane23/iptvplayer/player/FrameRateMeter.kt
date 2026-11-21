package com.bane23.iptvplayer.player

import kotlin.math.abs

/**
 * Meri broj sličica u sekundi iz vremena prikaza uzastopnih slika, za strimove koji ga sami ne
 * javljaju (česti kod kanala uživo). Računa prosečan razmak poslednjih slika bez onih koji odskaču,
 * pa ispuštene slike i premotavanje ne kvare rezultat. Čist Kotlin, bez Androida, da može da se testira.
 *
 * Slike stižu sa niti plejera, a rezultat se čita sa glavne, pa je sve zaključano.
 */
class FrameRateMeter {
    private val gaps = LongArray(WINDOW)
    private var count = 0
    private var next = 0
    private var lastTimeUs = NO_TIME

    /** Nova slika, sa svojim vremenom prikaza u mikrosekundama. */
    @Synchronized
    fun onFrame(presentationTimeUs: Long) {
        val gap = presentationTimeUs - lastTimeUs
        val hadLast = lastTimeUs != NO_TIME
        lastTimeUs = presentationTimeUs
        // Skok unazad ili veliki skok unapred je premotavanje ili prekid, ne razmak između slika.
        if (!hadLast || gap <= 0 || gap > MAX_GAP_US) return
        gaps[next] = gap
        next = (next + 1) % WINDOW
        if (count < WINDOW) count++
    }

    @Synchronized
    fun reset() {
        count = 0
        next = 0
        lastTimeUs = NO_TIME
    }

    /** Izmereni broj sličica u sekundi, ili 0 dok nema dovoljno slika. */
    @Synchronized
    fun frameRate(): Float {
        if (count < MIN_SAMPLES) return 0f
        val sorted = gaps.copyOf(count).also { it.sort() }
        val median = sorted[count / 2]
        // Razmak dalek od srednjeg je ispuštena slika; prosek ostalih je tačniji od samog srednjeg
        // kad su vremena zaokružena (npr. na milisekunde).
        val regular = sorted.filter { it * 2 > median && it * 2 < median * 3 }
        val measured = (1_000_000.0 / regular.average()).toFloat()
        // Mereno je uvek malo pored prave vrednosti; uobičajene se ispisuju tačno.
        val nearest = COMMON_RATES.minBy { abs(measured - it) }
        return if (abs(measured - nearest) / nearest < SNAP_TOLERANCE) nearest else measured
    }

    private companion object {
        const val WINDOW = 48
        const val MIN_SAMPLES = 12
        const val MAX_GAP_US = 1_000_000L
        const val NO_TIME = Long.MIN_VALUE
        const val SNAP_TOLERANCE = 0.004f
        val COMMON_RATES = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
    }
}
