package com.bane23.iptvplayer.data.epg

import java.io.Reader

/** Jedan element XMLTV vodiča. */
sealed interface XmltvItem {
    /** Opis kanala: ID na koji se pozivaju emisije i nazivi pod kojima se kanal vodi. */
    data class Channel(val id: String, val displayNames: List<String>) : XmltvItem

    /** Emisija. Vremena su u milisekundama od 1970. (UTC). */
    data class Program(
        val channelId: String,
        val startMs: Long,
        val stopMs: Long,
        val title: String,
        val description: String?,
    ) : XmltvItem
}

/**
 * Parser XMLTV vodiča. Čita ulaz deo po deo i vraća elemente redom, bez učitavanja celog
 * fajla u memoriju (vodiči umeju da imaju stotine megabajta).
 *
 * Emisije bez početka, kraja, kanala ili naslova se preskaču.
 */
object XmltvParser {

    fun parse(reader: Reader): Sequence<XmltvItem> = sequence {
        val xml = XmlScanner(reader)
        while (true) {
            val tag = xml.nextStartTag() ?: break
            when (tag.name) {
                "channel" -> readChannel(xml, tag)?.let { yield(it) }
                "programme" -> readProgram(xml, tag)?.let { yield(it) }
            }
        }
    }

    private fun readChannel(xml: XmlScanner, tag: XmlScanner.Tag): XmltvItem.Channel? {
        val id = tag.attributes["id"]?.trim().orEmpty()
        val names = ArrayList<String>(2)
        if (!tag.selfClosing) {
            xml.readChildren("channel") { child ->
                if (child.name == "display-name" && !child.selfClosing) {
                    val name = xml.readText("display-name").trim()
                    if (name.isNotEmpty()) names.add(name)
                } else {
                    xml.skip(child)
                }
            }
        }
        return if (id.isEmpty() || xml.ended) null else XmltvItem.Channel(id, names)
    }

    private fun readProgram(xml: XmlScanner, tag: XmlScanner.Tag): XmltvItem.Program? {
        val channelId = tag.attributes["channel"]?.trim().orEmpty()
        val start = tag.attributes["start"]?.let(::parseTime)
        val stop = tag.attributes["stop"]?.let(::parseTime)
        var title: String? = null
        var description: String? = null
        if (!tag.selfClosing) {
            xml.readChildren("programme") { child ->
                when {
                    child.selfClosing -> Unit
                    // Ako ima više naslova (na više jezika), uzima se prvi.
                    child.name == "title" && title == null -> title = xml.readText("title").trim()
                    child.name == "desc" && description == null -> description = xml.readText("desc").trim()
                    else -> xml.skip(child)
                }
            }
        }
        // Emisija na kojoj je ulaz prekinut je nepotpuna.
        if (xml.ended) return null
        val cleanTitle = title?.takeIf { it.isNotEmpty() }
        if (channelId.isEmpty() || start == null || stop == null || stop <= start || cleanTitle == null) return null
        return XmltvItem.Program(channelId, start, stop, cleanTitle, description?.takeIf { it.isNotEmpty() })
    }

    /**
     * XMLTV vreme: `YYYYMMDDhhmmss` (kraći oblici su dozvoljeni) i opciona zona `+hhmm` / `-hhmm`.
     * Bez zone se smatra da je vreme u UTC. Vraća null ako zapis nije ispravan.
     */
    fun parseTime(text: String): Long? {
        val value = text.trim()
        val digits = value.takeWhile { it.isDigit() }
        if (digits.length < 8 || digits.length % 2 != 0 || digits.length > 14) return null
        fun part(from: Int, to: Int, default: Int): Int =
            if (digits.length >= to) digits.substring(from, to).toInt() else default
        val year = part(0, 4, 0)
        val month = part(4, 6, 1)
        val day = part(6, 8, 1)
        val hour = part(8, 10, 0)
        val minute = part(10, 12, 0)
        val second = part(12, 14, 0)
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

        val zone = value.substring(digits.length).trim()
        val offsetMinutes = when {
            zone.isEmpty() || zone.equals("UTC", ignoreCase = true) || zone == "Z" -> 0
            else -> parseOffset(zone) ?: return null
        }
        val seconds = daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second
        return (seconds - offsetMinutes * 60L) * 1_000L
    }

    private fun parseOffset(zone: String): Int? {
        val sign = when (zone[0]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val digits = zone.substring(1).replace(":", "")
        if (digits.length != 4 || !digits.all { it.isDigit() }) return null
        return sign * (digits.substring(0, 2).toInt() * 60 + digits.substring(2, 4).toInt())
    }

    /** Broj dana od 1.1.1970. do datog datuma (gregorijanski kalendar). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }
}
