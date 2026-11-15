package com.bane23.iptvplayer.data.m3u

/** Jedna stavka iz M3U liste. */
sealed interface M3uItem {
    /** Zaglavlje `#EXTM3U`; nosi link ka TV vodiču ako ga lista navodi. */
    data class Header(val epgUrl: String?) : M3uItem

    data class Entry(
        val name: String,
        val url: String,
        val tvgId: String? = null,
        val tvgName: String? = null,
        val logo: String? = null,
        val group: String? = null,
        val number: Int? = null,
        val catchupType: String? = null,
        val catchupSource: String? = null,
        val catchupDays: Int? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
    ) : M3uItem
}

/**
 * Strimovan M3U parser: čita red po red i vraća stavke kao sekvencu, pa lista od
 * više desetina hiljada kanala nikad nije cela u memoriji. Loše formirani redovi se preskaču.
 */
object M3uParser {

    fun parse(lines: Sequence<String>): Sequence<M3uItem> = sequence {
        var pending: PendingEntry? = null
        var first = true
        for (raw in lines) {
            var line = raw.trim()
            if (first) {
                line = line.removePrefix("﻿").trim()
            }
            if (line.isEmpty()) continue
            first = false

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substring("#EXTM3U".length)).attributes
                    val epg = (attrs["url-tvg"] ?: attrs["x-tvg-url"])
                        ?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    yield(M3uItem.Header(epg))
                }

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pending = parseExtInf(line.substring("#EXTINF:".length))
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val group = line.substring("#EXTGRP:".length).trim()
                    if (group.isNotEmpty() && pending != null && pending.group == null) {
                        pending.group = group
                    }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substring("#EXTVLCOPT:".length)
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim()
                    if (pending != null && value.isNotEmpty()) {
                        when (key) {
                            "http-user-agent" -> pending.userAgent = value
                            "http-referrer", "http-referer" -> pending.referrer = value
                        }
                    }
                }

                line.startsWith("#") -> Unit // nepoznata direktiva ili komentar

                else -> {
                    val entry = pending
                    pending = null
                    if (entry != null) {
                        yield(entry.toEntry(line))
                    }
                }
            }
        }
    }

    private class PendingEntry(
        val name: String,
        val attributes: Map<String, String>,
        var group: String?,
        var userAgent: String? = null,
        var referrer: String? = null,
    ) {
        fun toEntry(url: String): M3uItem.Entry {
            val tvgName = attributes["tvg-name"]
            return M3uItem.Entry(
                name = name.ifEmpty { tvgName ?: url },
                url = url,
                tvgId = attributes["tvg-id"],
                tvgName = tvgName,
                logo = attributes["tvg-logo"] ?: attributes["logo"],
                group = group,
                number = (attributes["tvg-chno"] ?: attributes["channel-number"])?.toIntOrNull(),
                catchupType = attributes["catchup"] ?: attributes["catchup-type"],
                catchupSource = attributes["catchup-source"],
                catchupDays = (attributes["catchup-days"] ?: attributes["timeshift"] ?: attributes["tvg-rec"])
                    ?.toIntOrNull(),
                userAgent = userAgent ?: attributes["user-agent"] ?: attributes["http-user-agent"],
                referrer = referrer,
            )
        }
    }

    private class ParsedAttributes(val attributes: Map<String, String>, val rest: String)

    private fun parseExtInf(body: String): PendingEntry {
        // Format: <trajanje> [ključ="vrednost" ...],<naziv>
        var i = 0
        while (i < body.length && body[i] != ' ' && body[i] != ',' && body[i] != '\t') i++
        val parsed = parseAttributes(body.substring(i))
        val name = parsed.rest.trim()
        return PendingEntry(
            name = name,
            attributes = parsed.attributes,
            group = parsed.attributes["group-title"],
        )
    }

    /**
     * Čita parove `ključ="vrednost"` (ili bez navodnika) do prvog zareza van navodnika.
     * Sve posle tog zareza vraća kao [ParsedAttributes.rest].
     */
    private fun parseAttributes(text: String): ParsedAttributes {
        val attributes = LinkedHashMap<String, String>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == ',') {
                return ParsedAttributes(attributes, text.substring(i + 1))
            }
            if (c.isWhitespace()) {
                i++
                continue
            }
            // ključ
            val keyStart = i
            while (i < n && text[i] != '=' && text[i] != ',' && !text[i].isWhitespace()) i++
            if (i >= n || text[i] != '=') {
                // reč bez "=": nije atribut, preskoči je
                continue
            }
            val key = text.substring(keyStart, i).lowercase()
            i++ // '='
            val value: String
            if (i < n && text[i] == '"') {
                val end = text.indexOf('"', i + 1)
                if (end == -1) {
                    // navodnik nije zatvoren: vrednost ide do zareza ili kraja reda
                    val comma = text.indexOf(',', i + 1)
                    val stop = if (comma == -1) n else comma
                    value = text.substring(i + 1, stop)
                    i = stop
                } else {
                    value = text.substring(i + 1, end)
                    i = end + 1
                }
            } else {
                val valueStart = i
                while (i < n && text[i] != ',' && !text[i].isWhitespace()) i++
                value = text.substring(valueStart, i)
            }
            val trimmed = value.trim()
            if (key.isNotEmpty() && trimmed.isNotEmpty()) {
                attributes[key] = trimmed
            }
        }
        return ParsedAttributes(attributes, "")
    }
}
