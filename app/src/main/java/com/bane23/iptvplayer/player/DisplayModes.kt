package com.bane23.iptvplayer.player

import kotlin.math.abs
import kotlin.math.roundToInt

/** Jedan režim ekrana: rezolucija i broj osvežavanja u sekundi. */
data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/** Izbor režima ekrana čije se osvežavanje poklapa sa brojem sličica snimka. */
object DisplayModes {
    /** Koliko osvežavanje sme da odstupi od umnoška broja sličica (24 Hz važi i za 23,976). */
    private const val TOLERANCE = 0.1f

    private fun mismatch(refreshRate: Float, frameRate: Float): Float {
        val times = (refreshRate / frameRate).roundToInt()
        return if (times < 1) Float.MAX_VALUE else abs(refreshRate - times * frameRate)
    }

    /**
     * ID režima na koji treba preći, ili null kad ne treba ništa menjati: trenutni režim već odgovara,
     * broj sličica se ne zna, ili ekran nema odgovarajući režim. Rezolucija se ne menja. Među
     * režimima koji se poklapaju bira se najtačniji, pa onaj sa više osvežavanja (50 Hz pre 25 Hz).
     */
    fun pick(modes: List<DisplayModeInfo>, currentId: Int, frameRate: Float): Int? {
        if (frameRate.isNaN() || frameRate < 10f) return null
        val current = modes.firstOrNull { it.id == currentId } ?: return null
        val best = modes
            .filter { it.width == current.width && it.height == current.height }
            .filter { mismatch(it.refreshRate, frameRate) < TOLERANCE }
            .sortedWith(
                compareBy<DisplayModeInfo> { (mismatch(it.refreshRate, frameRate) * 100).roundToInt() }
                    .thenByDescending { it.refreshRate },
            )
            .firstOrNull() ?: return null
        if (best.id == current.id) return null
        // Trenutni režim je jednako dobar (npr. 50 Hz za 25 sličica): prebacivanje bi samo zacrnilo ekran.
        val currentMismatch = mismatch(current.refreshRate, frameRate)
        if (currentMismatch < TOLERANCE && (currentMismatch * 100).roundToInt() <= (mismatch(best.refreshRate, frameRate) * 100).roundToInt()) {
            return null
        }
        return best.id
    }
}
