package com.bane23.iptvplayer.ui.common.player

import androidx.media3.common.C
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Tekst za prozor „Informacije o strimu”. Čist Kotlin, bez Androida, da može da se testira.
 * Nepoznati podaci (nula, -1, null) se preskaču.
 */

/** Npr. „3840×2160 · 50 fps · H.265 · HDR HLG · 25.0 Mb/s”, ili null kad se ništa ne zna. */
fun videoInfoLine(
    width: Int,
    height: Int,
    frameRate: Float,
    mimeType: String?,
    bitrate: Int,
    colorTransfer: Int = C.INDEX_UNSET,
    colorSpace: Int = C.INDEX_UNSET,
): String? =
    listOfNotNull(
        if (width > 0 && height > 0) "$width×$height" else null,
        frameRateLabel(frameRate),
        codecLabel(mimeType),
        colorLabel(colorTransfer, colorSpace),
        bitrateLabel(bitrate),
    ).joinToString("  ·  ").ifEmpty { null }

/**
 * Vrsta slike kad nije obična (SDR, BT.709): HDR po prenosnoj funkciji, inače širi opseg boja.
 * Obična slika i nepoznate vrednosti ne daju ništa.
 */
internal fun colorLabel(colorTransfer: Int, colorSpace: Int): String? = when (colorTransfer) {
    C.COLOR_TRANSFER_HLG -> "HDR HLG"
    C.COLOR_TRANSFER_ST2084 -> "HDR10"
    else -> if (colorSpace == C.COLOR_SPACE_BT2020) "BT.2020" else null
}

/** Npr. „AAC · 2.0 · 48 kHz · 128 kb/s”, ili null kad se ništa ne zna. */
fun audioInfoLine(mimeType: String?, channels: Int, sampleRate: Int, bitrate: Int): String? =
    listOfNotNull(
        codecLabel(mimeType),
        channelsLabel(channels),
        if (sampleRate > 0) String.format(Locale.US, "%.1f kHz", sampleRate / 1000f).replace(".0 ", " ") else null,
        bitrateLabel(bitrate),
    ).joinToString("  ·  ").ifEmpty { null }

internal fun frameRateLabel(frameRate: Float): String? = when {
    frameRate <= 0f || frameRate.isNaN() -> null
    abs(frameRate - frameRate.roundToInt()) < 0.01f -> "${frameRate.roundToInt()} fps"
    else -> String.format(Locale.US, "%.2f fps", frameRate)
}

internal fun bitrateLabel(bitrate: Int): String? = when {
    bitrate <= 0 -> null
    bitrate >= 1_000_000 -> String.format(Locale.US, "%.1f Mb/s", bitrate / 1_000_000f)
    else -> "${(bitrate / 1000f).roundToInt()} kb/s"
}

internal fun channelsLabel(channels: Int): String? = when (channels) {
    1 -> "1.0"
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> if (channels > 0) "$channels ch" else null
}

/** Uobičajeno ime kodeka iz MIME tipa (video/avc je H.264); nepoznat tip ostaje kakav jeste. */
internal fun codecLabel(mimeType: String?): String? = when (mimeType?.lowercase(Locale.US)) {
    null, "" -> null
    "video/avc" -> "H.264"
    "video/hevc" -> "H.265"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/av01" -> "AV1"
    "video/mpeg2" -> "MPEG-2"
    "video/mp4v-es" -> "MPEG-4"
    "audio/mp4a-latm" -> "AAC"
    "audio/ac3" -> "AC-3"
    "audio/eac3", "audio/eac3-joc" -> "E-AC-3"
    "audio/mpeg" -> "MP3"
    "audio/mpeg-l2" -> "MP2"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/flac" -> "FLAC"
    else -> mimeType.substringAfter('/').uppercase(Locale.US)
}
