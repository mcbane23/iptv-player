package com.bane23.iptvplayer.player

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Jedan red titla: od kog do kog trenutka sadržaja (u milisekundama) se prikazuje [text]. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Titl iz SubRip (.srt) fajla, pročitan u memoriju. Prikazuje ga aplikacija preko slike, pa uključivanje,
 * zamena i pomeranje titla ne diraju plejer: slika i zvuk idu dalje bez prekida.
 */
class SrtCues(cues: List<SubtitleCue>) {
    val cues: List<SubtitleCue> = cues.sortedBy { it.startMs }

    /** Tekst koji se prikazuje u trenutku [positionMs], ili null kad tada nema titla. */
    fun textAt(positionMs: Long): String? {
        // Poslednji red koji je počeo do ovog trenutka.
        var low = 0
        var high = cues.size - 1
        var last = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                last = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (last < 0) return null
        // Redovi mogu da se preklapaju, pa se gleda i nekoliko prethodnih.
        val active = (maxOf(0, last - OVERLAP_LOOKBACK)..last).filter { positionMs < cues[it].endMs }
        return if (active.isEmpty()) null else active.joinToString("\n") { cues[it].text }
    }

    companion object {
        private const val OVERLAP_LOOKBACK = 4

        private const val TIME = "(\\d{1,3}):(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})"
        private val timing = Regex("^\\s*$TIME\\s*-->\\s*$TIME.*$")

        /** Oznake izgleda („<i>”, „<font color=...>”, „{\an8}”); izgled titla određuju podešavanja. */
        private val tags = Regex("</?[a-zA-Z][^>]*>|\\{\\\\[^}]*\\}")

        /** Čita tekst .srt fajla. Redovi bez ispravnog vremena ili bez teksta se preskaču. */
        fun parse(text: String): SrtCues {
            val lines = text.removePrefix("\uFEFF").lines()
            val cues = ArrayList<SubtitleCue>()
            var index = 0
            while (index < lines.size) {
                val match = timing.matchEntire(lines[index++]) ?: continue
                val v = match.groupValues
                val start = millis(v[1], v[2], v[3], v[4])
                val end = millis(v[5], v[6], v[7], v[8])
                val body = ArrayList<String>()
                while (index < lines.size && lines[index].isNotBlank()) {
                    if (timing.matches(lines[index])) {
                        // Nema praznog reda između dva titla: poslednji red je redni broj sledećeg.
                        if (body.lastOrNull()?.trim()?.all { it.isDigit() } == true) body.removeAt(body.size - 1)
                        break
                    }
                    body.add(lines[index++])
                }
                val content = body.map { tags.replace(it, "").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                if (content.isNotEmpty() && end > start) cues.add(SubtitleCue(start, end, content))
            }
            return SrtCues(cues)
        }

        /**
         * Pretvara sadržaj fajla u tekst: UTF-16 i UTF-8 po oznaci na početku, inače UTF-8, a ako
         * fajl nije ispravan UTF-8, stari srednjoevropski zapis (windows-1250), čest kod naših titlova.
         */
        fun decode(bytes: ByteArray): String {
            fun starts(vararg head: Int) = bytes.size >= head.size && head.indices.all { bytes[it] == head[it].toByte() }
            if (starts(0xFF, 0xFE) || starts(0xFE, 0xFF)) return String(bytes, Charsets.UTF_16)
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, legacyCharset)
            }
        }

        private val legacyCharset: Charset by lazy {
            try {
                Charset.forName("windows-1250")
            } catch (_: Exception) {
                Charsets.ISO_8859_1
            }
        }

        private fun millis(h: String, m: String, s: String, fraction: String): Long =
            h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1_000 + fraction.padEnd(3, '0').toLong()
    }
}
