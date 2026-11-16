package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Emisija iz arhive Xtream kanala. */
data class XtreamArchiveEntry(
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?,
    /** Početak po satu servera, u obliku koji traži link arhive (`2026-01-31:20-00`). */
    val startToken: String,
) {
    /** Trajanje u celim minutima, zaokruženo naviše, kako ga traži link arhive. */
    val durationMinutes: Long get() = ((stopMs - startMs) + 59_999) / 60_000
}

/**
 * Čita odgovor `get_simple_data_table`: emisije jednog kanala, sa naslovima u Base64. Vreme za
 * link arhive mora biti po satu servera, pa se uzima iz polja `start` onako kako ga je panel poslao.
 */
object XtreamArchiveParser {
    /** Završene emisije koje su počele posle [earliestMs], najnovije prvo. */
    fun parse(response: JsonElement, earliestMs: Long, nowMs: Long): List<XtreamArchiveEntry> {
        val listings = (response as? JsonObject)?.get("epg_listings") as? JsonArray ?: return emptyList()
        return listings.mapNotNull { item ->
            val entry = item as? JsonObject ?: return@mapNotNull null
            val start = entry.text("start_timestamp")?.toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val stop = entry.text("stop_timestamp")?.toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val token = startToken(entry.text("start")) ?: return@mapNotNull null
            if (stop <= start || stop > nowMs || start < earliestMs) return@mapNotNull null
            XtreamArchiveEntry(
                startMs = start,
                stopMs = stop,
                title = decode(entry.text("title")).ifBlank { return@mapNotNull null },
                description = decode(entry.text("description")).takeIf { it.isNotBlank() },
                startToken = token,
            )
        }.distinctBy { it.startMs }.sortedByDescending { it.startMs }
    }

    /** `2026-01-31 20:00:00` → `2026-01-31:20-00`. */
    fun startToken(start: String?): String? {
        val match = START.matchEntire(start?.trim().orEmpty()) ?: return null
        val (date, hour, minute) = match.destructured
        return "$date:$hour-$minute"
    }

    private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.content

    /** Naslovi stižu u Base64; ako tekst to nije, vraća se onakav kakav je. */
    fun decode(text: String?): String {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        return decodeBase64(clean)?.let { String(it, Charsets.UTF_8).trim() } ?: clean
    }

    /** Sopstveno čitanje Base64, jer `java.util.Base64` ne postoji na starijim Android uređajima. */
    private fun decodeBase64(text: String): ByteArray? {
        val data = text.trimEnd('=')
        if (data.isEmpty() || data.length % 4 == 1) return null
        val out = ByteArray(data.length * 6 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in data) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[index++] = (buffer shr bits and 0xFF).toByte()
            }
        }
        return out
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val START = Regex("""(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})(?::\d{2})?""")
}
