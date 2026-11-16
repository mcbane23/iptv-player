package com.bane23.iptvplayer.data.lan

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder

/**
 * Jedan HTTP zahtev sa telefona. [form] su polja iz tela POST zahteva, [host] je adresa na koju se
 * telefon obratio (zaglavlje Host), a [body] sirovo telo kada zahtev nosi paket umesto forme.
 */
data class PairingRequest(
    val method: String,
    val path: String,
    val form: Map<String, String>,
    val host: String = "",
    val body: String = "",
)

/**
 * Najmanji deo HTTP-a koji je potreban da telefon u istoj mreži otvori stranicu sa TV-a i pošalje
 * formu. Namerno bez spoljne biblioteke: prima samo GET i POST sa malim telom.
 */
object PairingHttp {
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_BODY_BYTES = 32 * 1024

    /** Paket sa svim listama može da nosi mnogo omiljenih kanala, pa sme da bude veći od forme. */
    private const val MAX_BUNDLE_BYTES = 2 * 1024 * 1024

    /** Putevi čije se telo čita sirovo (JSON), vidi `CastServer`. */
    private const val RAW_BODY_PREFIX = "/cast/"

    /** Čita zahtev. Vraća null ako nije ispravan ili je prevelik. */
    fun readRequest(input: InputStream): PairingRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val requestLine = lines.first().split(' ')
        if (requestLine.size < 2) return null
        val method = requestLine[0].uppercase()
        val path = requestLine[1].substringBefore('?')
        var contentLength = 0
        var host = ""
        for (line in lines.drop(1)) {
            val name = line.substringBefore(':').trim()
            if (name.equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: return null
            } else if (name.equals("Host", ignoreCase = true)) {
                host = line.substringAfter(':').trim()
            }
        }
        val isBundle = path.endsWith(TransferTarget.BUNDLE_PATH)
        // Zahtevi za puštanje na TV-u nose JSON, a ne formu.
        val isRaw = isBundle || path.startsWith(RAW_BODY_PREFIX)
        if (contentLength < 0 || contentLength > (if (isBundle) MAX_BUNDLE_BYTES else MAX_BODY_BYTES)) return null
        var rawBody = ""
        val form = if (method == "POST" && contentLength > 0) {
            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val count = input.read(body, read, contentLength - read)
                if (count < 0) return null
                read += count
            }
            val text = String(body, Charsets.UTF_8)
            if (isRaw) {
                rawBody = text
                emptyMap()
            } else {
                decodeForm(text)
            }
        } else {
            emptyMap()
        }
        return PairingRequest(method, path, form, host, rawBody)
    }

    /** Čita zaglavlje do praznog reda, bajt po bajt, da se ne bi pročitao i deo tela. */
    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (buffer.size() < MAX_HEADER_BYTES) {
            val byte = input.read()
            if (byte < 0) return null
            buffer.write(byte)
            matched = when {
                byte == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                byte == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                byte == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) return String(buffer.toByteArray(), Charsets.ISO_8859_1).trimEnd()
        }
        return null
    }

    /** Polja forme oblika `a=1&b=2` (application/x-www-form-urlencoded). */
    fun decodeForm(body: String): Map<String, String> = body.split('&')
        .filter { it.isNotEmpty() }
        .mapNotNull { pair ->
            try {
                val name = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                name to value
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        .toMap()

    /** Ceo odgovor (zaglavlje i telo) za HTML stranicu. */
    fun htmlResponse(status: Int, html: String): ByteArray = response(status, "text/html; charset=utf-8", html)

    /** Odgovor aplikaciji na telefonu (ishod prenosa). */
    fun jsonResponse(status: Int, json: String): ByteArray = response(status, "application/json; charset=utf-8", json)

    private fun response(status: Int, contentType: String, text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.ISO_8859_1) + body
    }
}
