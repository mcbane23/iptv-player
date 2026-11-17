package com.bane23.iptvplayer.player

import java.io.File

/** Pomeranje SubRip (.srt) titla u vremenu, kad kasni ili žuri u odnosu na sliku. */
object SrtShift {
    private val time = Regex("(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")

    /**
     * Vraća titl u kom je svako vreme pomereno za [offsetMs] (pozitivno = titl izlazi kasnije).
     * Menjaju se samo redovi sa vremenima („00:01:02,500 --> 00:01:04,000”); vreme ne ide ispod nule.
     */
    fun shift(text: String, offsetMs: Long): String {
        if (offsetMs == 0L) return text
        return text.lineSequence().joinToString("\n") { line ->
            if ("-->" !in line) line else time.replace(line) { match ->
                val (h, m, s, frac) = match.destructured
                val total = h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1_000 + frac.padEnd(3, '0').toLong()
                format((total + offsetMs).coerceAtLeast(0))
            }
        }
    }

    private fun format(ms: Long): String =
        "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1_000 % 60, ms % 1_000)

    /**
     * Pravi pomerenu kopiju fajla [source] u [target]. Slova se ne diraju: fajl se čita bajt po bajt
     * (vremena su obične cifre), osim UTF-16 fajlova, koji se prepisuju kao UTF-8.
     */
    fun shiftFile(source: File, target: File, offsetMs: Long) {
        val bytes = source.readBytes()
        val utf16 = bytes.size >= 2 &&
            ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
        target.parentFile?.mkdirs()
        if (utf16) {
            target.writeText(shift(String(bytes, Charsets.UTF_16).removePrefix("\uFEFF"), offsetMs), Charsets.UTF_8)
        } else {
            target.writeBytes(shift(String(bytes, Charsets.ISO_8859_1), offsetMs).toByteArray(Charsets.ISO_8859_1))
        }
    }
}
