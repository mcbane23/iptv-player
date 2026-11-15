package com.bane23.iptvplayer.data.epg

import java.io.Reader

/**
 * Mali strimovani čitač XML-a, dovoljan za XMLTV: tagovi, atributi, tekst, CDATA i entiteti.
 * Komentari, deklaracije i DOCTYPE se preskaču. Ne proverava da li je dokument ispravan;
 * oštećen ili prekinut ulaz se čita dokle može.
 */
internal class XmlScanner(private val reader: Reader) {

    class Tag(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean)

    private val buffer = CharArray(BUFFER_SIZE)
    private var position = 0
    private var limit = 0
    private val text = StringBuilder()

    /** Ulaz je potrošen. Element koji se čitao u tom trenutku je možda nepotpun. */
    var ended = false
        private set

    /** Sledeći otvarajući tag bilo gde u nastavku dokumenta, ili null na kraju. */
    fun nextStartTag(): Tag? {
        while (true) {
            if (!skipTo('<')) return null
            val tag = readMarkup(collectText = false)
            if (tag is Tag) return tag
            if (tag == END_OF_INPUT) return null
        }
    }

    /**
     * Čita decu elementa [parent] do njegovog zatvarajućeg taga. [onChild] mora da potroši
     * svako dete koje nije samozatvarajuće (pozivom [readText] ili [skip]).
     */
    fun readChildren(parent: String, onChild: (Tag) -> Unit) {
        while (true) {
            if (!skipTo('<')) return
            when (val markup = readMarkup(collectText = false)) {
                is Tag -> onChild(markup)
                is String -> if (markup == parent) return
                END_OF_INPUT -> return
            }
        }
    }

    /** Tekst elementa [name] do njegovog zatvarajućeg taga; ugnežđeni tagovi se zanemaruju. */
    fun readText(name: String): String {
        text.setLength(0)
        var depth = 0
        while (true) {
            val c = read()
            if (c < 0) break
            if (c == '<'.code) {
                when (val markup = readMarkup(collectText = true)) {
                    is Tag -> if (!markup.selfClosing) depth++
                    is String -> if (depth == 0 && markup == name) break else if (depth > 0) depth--
                    END_OF_INPUT -> break
                }
            } else if (c == '&'.code) {
                readEntity(text)
            } else {
                text.append(c.toChar())
            }
        }
        return text.toString()
    }

    /** Preskače ceo element čiji je otvarajući tag [tag]. */
    fun skip(tag: Tag) {
        if (tag.selfClosing) return
        var depth = 1
        while (depth > 0) {
            if (!skipTo('<')) return
            when (val markup = readMarkup(collectText = false)) {
                is Tag -> if (!markup.selfClosing) depth++
                is String -> depth--
                END_OF_INPUT -> return
            }
        }
    }

    /**
     * Čita ono što sledi posle `<`. Vraća [Tag] za otvarajući tag, naziv (String) za zatvarajući,
     * [SKIPPED] za komentar, deklaraciju i sl., a [END_OF_INPUT] ako je ulaz prekinut.
     * CDATA se dodaje u [text] kad je [collectText] uključen.
     */
    private fun readMarkup(collectText: Boolean): Any {
        val first = read()
        when {
            first < 0 -> return END_OF_INPUT
            first == '/'.code -> {
                val name = StringBuilder()
                while (true) {
                    val c = read()
                    if (c < 0) return END_OF_INPUT
                    if (c == '>'.code) break
                    name.append(c.toChar())
                }
                return name.toString().trim()
            }

            first == '?'.code -> return if (skipPast("?>")) SKIPPED else END_OF_INPUT
            first == '!'.code -> return readDeclaration(collectText)
        }

        val name = StringBuilder().append(first.toChar())
        var c: Int
        while (true) {
            c = read()
            if (c < 0) return END_OF_INPUT
            if (c == '>'.code || c == '/'.code || c.toChar().isWhitespace()) break
            name.append(c.toChar())
        }
        var attributes: MutableMap<String, String>? = null
        var selfClosing = false
        while (c != '>'.code) {
            if (c < 0) return END_OF_INPUT
            when {
                c == '/'.code -> selfClosing = true
                !c.toChar().isWhitespace() -> {
                    val attrName = StringBuilder().append(c.toChar())
                    while (true) {
                        c = read()
                        if (c < 0) return END_OF_INPUT
                        if (c == '='.code || c == '>'.code || c == '/'.code || c.toChar().isWhitespace()) break
                        attrName.append(c.toChar())
                    }
                    while (c >= 0 && c.toChar().isWhitespace()) c = read()
                    if (c != '='.code) continue
                    c = read()
                    while (c >= 0 && c.toChar().isWhitespace()) c = read()
                    if (c != '"'.code && c != '\''.code) continue
                    val quote = c
                    val value = StringBuilder()
                    while (true) {
                        c = read()
                        if (c < 0) return END_OF_INPUT
                        if (c == quote) break
                        if (c == '&'.code) readEntity(value) else value.append(c.toChar())
                    }
                    if (attributes == null) attributes = HashMap(4)
                    attributes[attrName.toString()] = value.toString()
                }
            }
            c = read()
        }
        return Tag(name.toString(), attributes ?: emptyMap(), selfClosing)
    }

    /** Posle `<!`: komentar, CDATA ili DOCTYPE. */
    private fun readDeclaration(collectText: Boolean): Any {
        val head = StringBuilder()
        while (head.length < CDATA_START.length) {
            val c = read()
            if (c < 0) return END_OF_INPUT
            head.append(c.toChar())
            if (head.length == 2 && head.toString() == "--") {
                return if (skipPast("-->")) SKIPPED else END_OF_INPUT
            }
            if (!CDATA_START.startsWith(head)) {
                // DOCTYPE i slično: do kraja deklaracije, uz ugnežđene zagrade.
                if (c == '>'.code) return SKIPPED
                var depth = 0
                while (true) {
                    val d = read()
                    if (d < 0) return END_OF_INPUT
                    if (d == '['.code) depth++
                    if (d == ']'.code) depth--
                    if (d == '>'.code && depth <= 0) return SKIPPED
                }
            }
        }
        // CDATA: sve do "]]>" je običan tekst.
        var brackets = 0
        while (true) {
            val c = read()
            if (c < 0) return END_OF_INPUT
            if (c == ']'.code) {
                brackets++
            } else if (c == '>'.code && brackets >= 2) {
                if (collectText) repeat(brackets - 2) { text.append(']') }
                return SKIPPED
            } else {
                if (collectText) {
                    repeat(brackets) { text.append(']') }
                    text.append(c.toChar())
                }
                brackets = 0
            }
        }
    }

    /** Posle `&`: prevodi entitet i dodaje ga u [out]. Nepoznat entitet ostaje kako je napisan. */
    private fun readEntity(out: StringBuilder) {
        val name = StringBuilder()
        while (name.length <= MAX_ENTITY_LENGTH) {
            val c = read()
            if (c < 0) break
            if (c == ';'.code) {
                val decoded = decodeEntity(name.toString())
                if (decoded != null) out.append(decoded) else out.append('&').append(name).append(';')
                return
            }
            if (c == '<'.code || c == '&'.code || c.toChar().isWhitespace()) {
                // Usamljeno "&" u neurednom fajlu: vrati znak da se pročita ponovo.
                position--
                break
            }
            name.append(c.toChar())
        }
        out.append('&').append(name)
    }

    private fun decodeEntity(name: String): String? = when (name) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        else -> if (name.startsWith("#")) {
            val code = if (name.startsWith("#x") || name.startsWith("#X")) {
                name.substring(2).toIntOrNull(16)
            } else {
                name.substring(1).toIntOrNull()
            }
            code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) }
        } else {
            null
        }
    }

    private fun skipTo(target: Char): Boolean {
        while (true) {
            val c = read()
            if (c < 0) return false
            if (c == target.code) return true
        }
    }

    private fun skipPast(end: String): Boolean {
        var matched = 0
        while (true) {
            val c = read()
            if (c < 0) return false
            matched = when {
                c == end[matched].code -> matched + 1
                // npr. "--->" : višak crtica ne poništava poklapanje
                matched == end.length - 1 && c == end[matched - 1].code -> matched
                c == end[0].code -> 1
                else -> 0
            }
            if (matched == end.length) return true
        }
    }

    private fun read(): Int {
        if (position >= limit) {
            // Poslednji znak se čuva na početku, da "position--" uvek ima gde da se vrati.
            val keep = if (limit > 0) 1 else 0
            if (keep == 1) buffer[0] = buffer[limit - 1]
            val count = reader.read(buffer, keep, buffer.size - keep)
            if (count <= 0) {
                ended = true
                return -1
            }
            position = keep
            limit = keep + count
        }
        return buffer[position++].code
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        const val MAX_ENTITY_LENGTH = 10
        const val CDATA_START = "[CDATA["
        val SKIPPED = Any()
        val END_OF_INPUT = Any()
    }
}
