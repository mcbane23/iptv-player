package com.bane23.iptvplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/** Koliko jako plejer izjednačava jačinu zvuka između kanala, filmova i serija. */
enum class LoudnessLevel {
    OFF,

    /** Sporo podešavanje ka istom nivou; razlike unutar filma uglavnom ostaju. */
    LIGHT,

    /** Brže podešavanje i veći raspon; glasne scene se jače stišaju, kao noćni režim. */
    STRONG,
}

/**
 * Trenutno izabran nivo. Plejer ga postavlja iz podešavanja kad se otvori, brzi meni plejera ga menja
 * u toku gledanja, a obrada zvuka ga čita pri svakom bloku.
 */
object LoudnessNormalization {
    val selected = MutableStateFlow(LoudnessLevel.OFF)

    var level: LoudnessLevel
        get() = selected.value
        set(value) {
            selected.value = value
        }
}

/**
 * Izjednačava jačinu zvuka: meri prosečnu jačinu poslednjih nekoliko sekundi i polako pomera pojačanje
 * ka istom ciljnom nivou, a vrhove seče da ne pucaju. Radi nad uzorcima kao brojevima od -1 do 1,
 * nezavisno od formata, da bi moglo da se proveri testovima.
 */
internal class LoudnessLeveler(val sampleRate: Int) {

    private class Tuning(
        val targetDb: Float,
        val maxBoostDb: Float,
        val maxCutDb: Float,
        /** Vreme za koje merenje jačine prati promenu, u sekundama. */
        val measureSeconds: Float,
        /** Koliko brzo se pojačanje smanjuje, odnosno povećava, u sekundama. */
        val cutSeconds: Float,
        val boostSeconds: Float,
    )

    private var measured = -1.0 // srednji kvadrat; negativno dok nema merenja
    private var gain = 1f
    private var limiterGain = 1f

    /** Sekunde zvuka od početka ili premotavanja; prvih par sekundi merenje brže prati zvuk. */
    private var heard = 0f

    /** Pojačanje koje se trenutno primenjuje (bez sečenja vrhova); za testove. */
    val currentGain: Float get() = gain

    fun reset() {
        measured = -1.0
        gain = 1f
        limiterGain = 1f
        heard = 0f
    }

    /**
     * Novi sadržaj ili premotavanje: merenje kreće iznova i prve sekunde brže prati zvuk, a pojačanje
     * ostaje, da glasan kanal posle tihog ne grune dok se ne izmeri.
     */
    fun restartMeasuring() {
        measured = -1.0
        heard = 0f
    }

    /**
     * Obrađuje [frames] okvira sa po [channels] uzoraka u [samples], na licu mesta. Uzorci se obrađuju
     * u blokovima od 10 ms: za svaki blok se osveži merenje i pomeri pojačanje.
     */
    fun process(samples: FloatArray, frames: Int, channels: Int, level: LoudnessLevel) {
        val tuning = TUNINGS[level]
        val block = maxOf(1, sampleRate / 100)
        var start = 0
        while (start < frames) {
            val end = minOf(frames, start + block)
            val target = if (tuning == null) 1f else targetGain(samples, start, end, channels, tuning)
            val seconds = (end - start).toFloat() / sampleRate
            val warmingUp = heard < WARMUP_SECONDS
            val timeConstant = when {
                tuning == null -> RETURN_SECONDS
                target < gain -> if (warmingUp) WARMUP_MEASURE_SECONDS else tuning.cutSeconds
                else -> tuning.boostSeconds
            }
            heard += seconds
            val from = gain
            gain += (target - gain) * (1f - exp(-seconds / timeConstant))
            if (tuning == null && abs(gain - 1f) < SNAP) gain = 1f
            apply(samples, start, end, channels, from, gain, tuning != null)
            start = end
        }
    }

    private fun targetGain(samples: FloatArray, start: Int, end: Int, channels: Int, tuning: Tuning): Float {
        var sum = 0.0
        for (i in start * channels until end * channels) sum += samples[i] * samples[i]
        val meanSquare = sum / ((end - start) * channels)
        // Tišina i vrlo tihi delovi (pauze, odjavna špica) ne menjaju merenje, da posle njih ne grune.
        if (meanSquare >= SILENCE) {
            val measureSeconds = if (heard < WARMUP_SECONDS) WARMUP_MEASURE_SECONDS else tuning.measureSeconds
            val alpha = 1.0 - exp(-(end - start).toDouble() / sampleRate / measureSeconds)
            measured = if (measured < 0) meanSquare else measured + (meanSquare - measured) * alpha
        }
        if (measured < 0) return gain
        val levelDb = 10.0 * log10(measured)
        val wantedDb = (tuning.targetDb - levelDb).toFloat().coerceIn(-tuning.maxCutDb, tuning.maxBoostDb)
        return 10f.pow(wantedDb / 20f)
    }

    private fun apply(
        samples: FloatArray,
        start: Int,
        end: Int,
        channels: Int,
        fromGain: Float,
        toGain: Float,
        active: Boolean,
    ) {
        if (!active && fromGain == 1f && toGain == 1f && limiterGain == 1f) return
        val count = end - start
        val release = 1f - exp(-1f / (LIMITER_RELEASE_SECONDS * sampleRate))
        for (f in 0 until count) {
            val g = fromGain + (toGain - fromGain) * (f + 1) / count
            val base = (start + f) * channels
            var peak = 0f
            for (c in 0 until channels) {
                val v = abs(samples[base + c] * g)
                if (v > peak) peak = v
            }
            limiterGain = when {
                peak * limiterGain > CEILING -> CEILING / peak
                limiterGain > 1f - SNAP -> 1f
                else -> limiterGain + (1f - limiterGain) * release
            }
            val total = g * limiterGain
            for (c in 0 until channels) samples[base + c] *= total
        }
    }

    private companion object {
        /** Vrhovi ne prelaze ovaj deo punog opsega. */
        const val CEILING = 0.95f
        const val LIMITER_RELEASE_SECONDS = 0.15f

        /** Prve sekunde posle početka ili premotavanja merenje i stišavanje brže prate zvuk. */
        const val WARMUP_SECONDS = 2f
        const val WARMUP_MEASURE_SECONDS = 0.4f

        /** Pojačanje ovoliko blizu 1 se računa kao 1. */
        const val SNAP = 0.001f

        /** Brzina vraćanja na običnu jačinu kad se izjednačavanje isključi. */
        const val RETURN_SECONDS = 0.5f

        /** Srednji kvadrat ispod -55 dB se smatra tišinom. */
        val SILENCE = 10.0.pow(-5.5)

        val TUNINGS = mapOf(
            LoudnessLevel.LIGHT to Tuning(
                targetDb = -20f, maxBoostDb = 10f, maxCutDb = 12f,
                measureSeconds = 3f, cutSeconds = 1.5f, boostSeconds = 4f,
            ),
            LoudnessLevel.STRONG to Tuning(
                targetDb = -19f, maxBoostDb = 15f, maxCutDb = 18f,
                measureSeconds = 0.8f, cutSeconds = 0.3f, boostSeconds = 1.5f,
            ),
        )
    }
}

/**
 * Obrada zvuka u plejeru koja izjednačava jačinu (vidi [LoudnessLeveler]). Aktivna je za svaki
 * raspakovan 16-bitni zvuk, a nivo čita iz [LoudnessNormalization] pri svakom bloku, pa se uključuje
 * i isključuje odmah, bez ponovnog pokretanja. Dok je isključena, samo prepisuje zvuk.
 */
@OptIn(UnstableApi::class)
internal class LoudnessNormalizerProcessor : BaseAudioProcessor() {

    private var leveler: LoudnessLeveler? = null
    private var buffer = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (2 * channels)
        val count = frames * channels
        val output = replaceOutputBuffer(count * 2)
        if (buffer.size < count) buffer = FloatArray(count)
        for (i in 0 until count) buffer[i] = inputBuffer.getShort() / SCALE
        leveler?.process(buffer, frames, channels, LoudnessNormalization.level)
        for (i in 0 until count) {
            output.putShort((buffer[i] * SCALE).coerceIn(-SCALE, SCALE - 1).toInt().toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        val sampleRate = inputAudioFormat.sampleRate
        val current = leveler
        if (current != null && current.sampleRate == sampleRate) current.restartMeasuring() else leveler = LoudnessLeveler(sampleRate)
    }

    override fun onReset() {
        leveler = null
        buffer = FloatArray(0)
    }

    private companion object {
        const val SCALE = 32768f
    }
}
