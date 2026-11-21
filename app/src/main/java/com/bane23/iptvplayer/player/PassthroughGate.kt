package com.bane23.iptvplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

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

    /** Zvuk koji se sada pušta ide neraspakovan. */
    @Volatile
    var passthrough = false
        private set

    /** Od poslednjeg brisanja je bar jednom odbijen zvuk koji bi inače išao neraspakovan. */
    @Volatile
    var refused = false

    private fun refuses(format: Format): Boolean {
        if (!pcmOnly || format.sampleMimeType == MimeTypes.AUDIO_RAW) return false
        refused = true
        return true
    }

    override fun supportsFormat(format: Format): Boolean = super.supportsFormat(format) && !refuses(format)

    override fun getFormatSupport(format: Format): Int {
        val support = super.getFormatSupport(format)
        return if (support != AudioSink.SINK_FORMAT_UNSUPPORTED && refuses(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else support
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        passthrough = inputFormat.sampleMimeType != MimeTypes.AUDIO_RAW
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }
}
