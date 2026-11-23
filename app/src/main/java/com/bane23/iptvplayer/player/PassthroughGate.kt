package com.bane23.iptvplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * Stoji ispred izlaza zvuka i po potrebi zabranjuje da zvuk ide neraspakovan ka TV-u ili pojačalu
 * (AC-3, E-AC-3, DTS...). Neraspakovan zvuk uvek ide običnom brzinom i vuče sliku za sobom, pa
 * promena brzine puštanja ne bi imala nikakvog dejstva. Dok je [pcmOnly] uključen, plejer takav
 * zvuk raspakuje sam (ili, ako uređaj nema dekoder za njega, pušta sliku bez tog zvuka).
 */
@OptIn(UnstableApi::class)
internal class PassthroughGate(sink: AudioSink) : ForwardingAudioSink(sink) {

    /** Dozvoljen je samo raspakovan zvuk. Važi od sledeće pripreme sadržaja. */
    @Volatile
    var pcmOnly = false

    /**
     * Raspakovan zvuk je poželjan (izjednačavanje jačine radi samo nad njim), ali samo ako uređaj ima
     * dekoder za taj zvuk; bez dekodera zvuk i dalje ide neraspakovan, da se ne izgubi. Važi od
     * sledeće pripreme sadržaja.
     */
    @Volatile
    var preferPcm = false

    /**
     * Od poslednjeg brisanja je bar jednom zbog [pcmOnly] odbijen zvuk koji bi inače išao
     * neraspakovan (i koji [preferPcm] ne bi odbio).
     */
    @Volatile
    var refused = false

    private fun refuses(format: Format): Boolean {
        val mime = format.sampleMimeType
        if (mime == MimeTypes.AUDIO_RAW) return false
        val preferred = preferPcm && mime != null && canDecode(mime)
        if (!pcmOnly && !preferred) return false
        if (!preferred) refused = true
        return true
    }

    override fun supportsFormat(format: Format): Boolean = super.supportsFormat(format) && !refuses(format)

    override fun getFormatSupport(format: Format): Int {
        val support = super.getFormatSupport(format)
        return if (support != AudioSink.SINK_FORMAT_UNSUPPORTED && refuses(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else support
    }

    private companion object {
        private val decodable = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

        fun canDecode(mime: String): Boolean = decodable.getOrPut(mime) {
            runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false).isNotEmpty() }.getOrDefault(false)
        }
    }
}
