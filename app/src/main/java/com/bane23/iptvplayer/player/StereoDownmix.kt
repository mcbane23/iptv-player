package com.bane23.iptvplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Neki uređaji (npr. projektor sa stereo zvučnikom) ne mogu da otvore izlaz za više od dva kanala, pa
 * film sa 5.1 zvukom javi grešku umesto da svira. Kad se to jednom desi, [enabled] se uključi do kraja
 * rada aplikacije i od tada se višekanalni zvuk spaja u stereo pre izlaza.
 */
internal object StereoDownmix {
    @Volatile
    var enabled = false
}

/**
 * Spaja raspakovan zvuk sa 3 do 8 kanala u dva, dok je [StereoDownmix.enabled] uključen; inače ne radi
 * ništa. Raspored kanala je Androidov: levo, desno, centar, LFE, pozadi levo, pozadi desno, bočno...
 */
@OptIn(UnstableApi::class)
internal class StereoDownmixProcessor : BaseAudioProcessor() {

    private var left = FloatArray(0)
    private var right = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val channels = inputAudioFormat.channelCount
        if (!StereoDownmix.enabled || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || channels !in 3..8) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val (l, r) = coefficients(channels)
        // Zbir koeficijenata je veći od 1, pa se ukupno malo stiša da glasni delovi ne pucaju.
        val gain = minOf(1f, MAX_SUM / l.sum())
        left = FloatArray(channels) { l[it] * gain }
        right = FloatArray(channels) { r[it] * gain }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = left.size
        val frames = inputBuffer.remaining() / (2 * channels)
        val output = replaceOutputBuffer(frames * 4)
        repeat(frames) {
            var l = 0f
            var r = 0f
            for (c in 0 until channels) {
                val sample = inputBuffer.getShort().toFloat()
                l += sample * left[c]
                r += sample * right[c]
            }
            output.putShort(clamp(l))
            output.putShort(clamp(r))
        }
        // Nepotpun okvir na kraju se ne može spojiti; odbacuje se da se ulaz ne zaglavi.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun clamp(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val MAX_SUM = 1.6f
        const val SIDE = 0.707f

        /** Udeo svakog kanala u levom i desnom izlazu, po broju ulaznih kanala. LFE se izostavlja. */
        fun coefficients(channels: Int): Pair<FloatArray, FloatArray> {
            // L, R, C, LFE, BL, BR, BC/SL, SR
            val layout: List<Pair<Float, Float>> = when (channels) {
                3 -> listOf(1f to 0f, 0f to 1f, SIDE to SIDE)
                4 -> listOf(1f to 0f, 0f to 1f, SIDE to 0f, 0f to SIDE)
                5 -> listOf(1f to 0f, 0f to 1f, SIDE to SIDE, SIDE to 0f, 0f to SIDE)
                6 -> listOf(1f to 0f, 0f to 1f, SIDE to SIDE, 0f to 0f, SIDE to 0f, 0f to SIDE)
                7 -> listOf(1f to 0f, 0f to 1f, SIDE to SIDE, 0f to 0f, SIDE to 0f, 0f to SIDE, 0.5f to 0.5f)
                else -> listOf(1f to 0f, 0f to 1f, SIDE to SIDE, 0f to 0f, SIDE to 0f, 0f to SIDE, SIDE to 0f, 0f to SIDE)
            }
            return FloatArray(channels) { layout[it].first } to FloatArray(channels) { layout[it].second }
        }
    }
}
