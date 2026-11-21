package com.bane23.iptvplayer.data.text

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Prepoznaje kodiranje teksta liste ili vodiča. Većina je UTF-8, ali starije liste i vodiči stižu
 * i u kodiranjima u kojima bi se č, ć, š, ž i đ inače prikazali pogrešno.
 */
object TextCharset {

    /** Kodiranje za tekst bez ikakve oznake koji nije ispravan UTF-8: srednjoevropsko (latinica sa kvačicama). */
    val FALLBACK: Charset = runCatching { Charset.forName("windows-1250") }.getOrDefault(Charsets.ISO_8859_1)

    /** Čitač koji tekst iz [input] čita u kodiranju prepoznatom iz njegovog početka. */
    fun reader(input: InputStream): Reader {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input, SAMPLE_BYTES)
        buffered.mark(SAMPLE_BYTES)
        val sample = ByteArray(SAMPLE_BYTES)
        var size = 0
        while (size < sample.size) {
            val read = buffered.read(sample, size, sample.size - size)
            if (read < 0) break
            size += read
        }
        buffered.reset()
        return InputStreamReader(buffered, detect(sample, size))
    }

    /**
     * Kodiranje teksta čijih je prvih [size] bajtova u [sample]: po oznaci na početku (BOM), pa po
     * XML deklaraciji (`encoding="..."`), a bez njih UTF-8 ako je uzorak ispravan UTF-8, inače [FALLBACK].
     */
    fun detect(sample: ByteArray, size: Int = sample.size): Charset {
        fun at(index: Int): Int = if (index < size) sample[index].toInt() and 0xFF else -1
        if (at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF) return Charsets.UTF_8
        if (at(0) == 0xFE && at(1) == 0xFF) return Charsets.UTF_16
        if (at(0) == 0xFF && at(1) == 0xFE) return Charsets.UTF_16
        declared(sample, size)?.let { return it }
        return if (isUtf8(sample, size)) Charsets.UTF_8 else FALLBACK
    }

    /** Kodiranje iz XML deklaracije na samom početku teksta, ako je ima i ako ga uređaj poznaje. */
    private fun declared(sample: ByteArray, size: Int): Charset? {
        val head = String(sample, 0, minOf(size, DECLARATION_BYTES), Charsets.ISO_8859_1)
        if (!head.trimStart().startsWith("<?xml")) return null
        val declaration = head.substringBefore("?>", "")
        val name = ENCODING.find(declaration)?.groupValues?.get(1) ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private fun isUtf8(sample: ByteArray, size: Int): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(sample, 0, size)
        val output = CharBuffer.allocate(4_096)
        while (true) {
            // Uzorak može da se završi usred znaka; to nije greška (endOfInput = false).
            val result = decoder.decode(input, output, false)
            if (result.isError) return false
            if (result.isUnderflow) return true
            output.clear()
        }
    }

    private val ENCODING = Regex("""encoding\s*=\s*["']([A-Za-z0-9._-]+)["']""")
    private const val SAMPLE_BYTES = 256 * 1024
    private const val DECLARATION_BYTES = 200
}
