package com.bane23.iptvplayer.data.recording

import java.net.URI

/** Jedan deo (segment) HLS strima. [sequence] raste za jedan sa svakim novim delom. */
data class HlsSegment(val sequence: Long, val url: String)

/** HLS spisak: ili spisak kvaliteta ([variants]), ili spisak delova ([segments]). */
data class HlsPlaylist(
    /** Adrese spiskova po kvalitetu, najbolji prvi. Prazno kad je ovo spisak delova. */
    val variants: List<String> = emptyList(),
    val segments: List<HlsSegment> = emptyList(),
    /** Trajanje jednog dela u sekundama; određuje koliko često se spisak ponovo čita. */
    val targetDurationSeconds: Int = DEFAULT_TARGET_SECONDS,
    /** Strim je završen, novih delova neće biti. */
    val ended: Boolean = false,
    /** Delovi nisu običan MPEG-TS (fMP4 ili šifrovano), pa ne mogu samo da se nadovežu u fajl. */
    val unsupported: Boolean = false,
) {
    companion object {
        const val DEFAULT_TARGET_SECONDS = 6

        fun isPlaylist(text: String): Boolean = text.trimStart('﻿', ' ', '\n', '\r').startsWith("#EXTM3U")

        /** Čita HLS spisak. Relativne adrese se sastavljaju prema [baseUrl]. */
        fun parse(text: String, baseUrl: String): HlsPlaylist {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val base = runCatching { URI(baseUrl) }.getOrNull()
            fun resolve(uri: String): String = runCatching { base?.resolve(uri)?.toString() }.getOrNull() ?: uri

            if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
                val variants = mutableListOf<Pair<Long, String>>()
                var bandwidth: Long? = null
                for (line in lines) {
                    when {
                        line.startsWith("#EXT-X-STREAM-INF") -> bandwidth = attribute(line, "BANDWIDTH")?.toLongOrNull() ?: 0
                        line.startsWith("#") -> Unit
                        bandwidth != null -> {
                            variants += bandwidth to resolve(line)
                            bandwidth = null
                        }
                    }
                }
                return HlsPlaylist(variants = variants.sortedByDescending { it.first }.map { it.second })
            }

            var sequence = 0L
            var target = DEFAULT_TARGET_SECONDS
            var ended = false
            var unsupported = false
            val segments = mutableListOf<HlsSegment>()
            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                        sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0
                    line.startsWith("#EXT-X-TARGETDURATION:") ->
                        target = line.substringAfter(':').trim().toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_TARGET_SECONDS
                    line.startsWith("#EXT-X-ENDLIST") -> ended = true
                    line.startsWith("#EXT-X-MAP") -> unsupported = true
                    line.startsWith("#EXT-X-KEY") ->
                        if (attribute(line, "METHOD")?.uppercase() != "NONE") unsupported = true
                    line.startsWith("#") -> Unit
                    else -> segments += HlsSegment(sequence++, resolve(line))
                }
            }
            return HlsPlaylist(segments = segments, targetDurationSeconds = target, ended = ended, unsupported = unsupported)
        }

        /** Vrednost atributa iz reda oblika `#TAG:A=1,B="x"`. */
        private fun attribute(line: String, name: String): String? =
            Regex("(?:^|[:,])$name=(\"[^\"]*\"|[^,]*)").find(line)?.groupValues?.get(1)?.trim('"')
    }
}
