package com.bane23.iptvplayer.data.lan

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferTest {
    private val texts = PairingTexts(
        title = "Add playlist", intro = "Intro", xtreamHeading = "Xtream", m3uHeading = "M3U",
        name = "Name", server = "Server", username = "User", password = "Pass", m3uUrl = "Link",
        epgUrl = "EPG", submit = "Send", wait = "Wait", doneTitle = "Done", doneText = "Look at the TV",
        notFound = "Scan again", appHeading = "From the app", appText = "Send everything", appButton = "Open the app",
    )

    private val bundle = TransferBundle(
        playlists = listOf(
            TransferPlaylist(
                type = TransferPlaylist.TYPE_XTREAM,
                name = "Kućna lista",
                source = "http://server.invalid:8080/",
                username = "izmisljen",
                password = "tajna \"sa\" znacima",
                epgShiftHours = -2,
                favorites = listOf("x:101", "x:202"),
                categories = listOf(
                    TransferCategory(name = "Sport", type = "LIVE", order = 0),
                    TransferCategory(name = "Vesti", type = "LIVE", hidden = true),
                ),
            ),
            TransferPlaylist(type = TransferPlaylist.TYPE_M3U, name = "Druga", source = "http://example.invalid/a.m3u"),
        ),
        settings = TransferSettings(resumeLastChannel = true, buffer = "LARGE", audioLanguage = "sr"),
    )

    @Test
    fun `bundle survives encoding and decoding`() {
        assertEquals(bundle, TransferCodec.decode(TransferCodec.encode(bundle)))
    }

    @Test
    fun `decoding ignores unknown fields and fills defaults`() {
        val decoded = TransferCodec.decode(
            """{"version":1,"future":true,"playlists":[{"type":"m3u","name":"A","source":"http://example.invalid/a.m3u","x":1}]}""",
        )!!
        assertEquals(1, decoded.playlists.size)
        assertTrue(decoded.playlists[0].favorites.isEmpty())
        assertNull(decoded.settings)
    }

    @Test
    fun `decoding rejects garbage and newer versions`() {
        assertNull(TransferCodec.decode("not json"))
        assertNull(TransferCodec.decode("""{"playlists":"oops"}"""))
        assertNull(TransferCodec.decode("""{"version":99}"""))
        assertNull(TransferCodec.decode("""{"version":0}"""))
    }

    @Test
    fun `target must be a token address of a device in the local network`() {
        assertEquals("http://192.168.1.20:40123/abc234def5", TransferTarget.validate("http://192.168.1.20:40123/abc234def5"))
        assertEquals("http://10.0.0.7:8080/abc234", TransferTarget.validate("http://10.0.0.7:8080/abc234"))
        assertEquals("http://172.20.1.1:5000/abc234", TransferTarget.validate("http://172.20.1.1:5000/abc234"))

        assertNull(TransferTarget.validate(null))
        assertNull(TransferTarget.validate(""))
        assertNull(TransferTarget.validate("https://192.168.1.20:40123/abc234def5"))
        assertNull(TransferTarget.validate("http://8.8.8.8:40123/abc234def5"))
        assertNull(TransferTarget.validate("http://172.32.0.1:40123/abc234def5"))
        assertNull(TransferTarget.validate("http://example.invalid:40123/abc234def5"))
        assertNull(TransferTarget.validate("http://192.168.1.20/abc234def5"))
        assertNull(TransferTarget.validate("http://192.168.1.20:99999/abc234def5"))
        assertNull(TransferTarget.validate("http://192.168.1.999:4000/abc234def5"))
        assertNull(TransferTarget.validate("http://user@192.168.1.20:40123/abc234def5"))
        assertNull(TransferTarget.validate("http://192.168.1.20:40123/abc234def5/more"))
        assertNull(TransferTarget.validate("http://192.168.1.20:40123/abc234def5?x=1"))
        assertNull(TransferTarget.validate("http://192.168.1.20:40123/"))
        assertNull(TransferTarget.validate("http://192.168.1.20:40123/a b"))
    }

    @Test
    fun `app link carries the target and shows only the address`() {
        val target = "http://192.168.1.20:40123/abc234def5"
        val link = TransferTarget.appLink(target)
        assertTrue(link.startsWith("iptvplayer://send?to="))
        assertEquals(target, URLDecoder.decode(link.substringAfter("to="), "UTF-8"))
        assertEquals("192.168.1.20:40123", TransferTarget.displayAddress(target))
    }

    @Test
    fun `page offers the app link only for a local address and when transfer is supported`() {
        val withTransfer = PairingServer(texts, onSubmit = { PairingResult.Added("x") }, token = "tok123", onBundle = { TransferResult() })
        val local = String(withTransfer.respond(PairingRequest("GET", "/tok123", emptyMap(), host = "192.168.1.20:40123")))
        assertTrue(local.contains("iptvplayer://send?to=http%3A%2F%2F192.168.1.20%3A40123%2Ftok123"))
        assertTrue(local.contains("Open the app"))

        val foreign = String(withTransfer.respond(PairingRequest("GET", "/tok123", emptyMap(), host = "evil.invalid\"><script>")))
        assertFalse(foreign.contains("iptvplayer://"))
        assertFalse(foreign.contains("<script>"))

        val withoutTransfer = PairingServer(texts, onSubmit = { PairingResult.Added("x") }, token = "tok123")
        val plain = String(withoutTransfer.respond(PairingRequest("GET", "/tok123", emptyMap(), host = "192.168.1.20:40123")))
        assertFalse(plain.contains("iptvplayer://"))
    }

    @Test
    fun `bundle route needs the token and a valid bundle`() {
        var received: TransferBundle? = null
        val server = PairingServer(
            texts,
            onSubmit = { PairingResult.Added("x") },
            token = "tok123",
            onBundle = {
                received = it
                TransferResult(added = 2)
            },
        )
        val body = TransferCodec.encode(bundle)
        assertTrue(String(server.respond(PairingRequest("POST", "/wrong/bundle", emptyMap(), body = body))).startsWith("HTTP/1.1 404"))
        assertTrue(String(server.respond(PairingRequest("POST", "/tok123/bundle", emptyMap(), body = "{oops"))).startsWith("HTTP/1.1 400"))
        assertTrue(String(server.respond(PairingRequest("GET", "/tok123/bundle", emptyMap()))).startsWith("HTTP/1.1 404"))
        assertNull(received)

        val ok = String(server.respond(PairingRequest("POST", "/tok123/bundle", emptyMap(), body = body)))
        assertTrue(ok.startsWith("HTTP/1.1 200"))
        assertEquals(TransferResult(added = 2), TransferCodec.decodeResult(ok.substringAfter("\r\n\r\n")))
        assertEquals(bundle, received)
    }

    @Test
    fun `request keeps the host and the raw body of a bundle`() {
        val body = """{"version":1}"""
        val raw = "POST /tok123/bundle HTTP/1.1\r\nHost: 192.168.1.20:40123\r\nContent-Length: ${body.length}\r\n\r\n$body"
        val parsed = PairingHttp.readRequest(ByteArrayInputStream(raw.toByteArray()))!!
        assertEquals("192.168.1.20:40123", parsed.host)
        assertEquals(body, parsed.body)
        assertTrue(parsed.form.isEmpty())
    }

    @Test
    fun `a form larger than the form limit is refused but a bundle of that size is read`() {
        val big = "a".repeat(100_000)
        val form = "POST /tok123 HTTP/1.1\r\nContent-Length: ${big.length}\r\n\r\n$big"
        assertNull(PairingHttp.readRequest(ByteArrayInputStream(form.toByteArray())))
        val asBundle = "POST /tok123/bundle HTTP/1.1\r\nContent-Length: ${big.length}\r\n\r\n$big"
        assertEquals(big, PairingHttp.readRequest(ByteArrayInputStream(asBundle.toByteArray()))?.body)
    }

    @Test
    fun `sends a bundle over a real socket`() {
        var received: TransferBundle? = null
        val server = PairingServer(
            texts,
            onSubmit = { PairingResult.Added("x") },
            onBundle = {
                received = it
                TransferResult(added = 1, existing = 1)
            },
        )
        val port = server.start()
        try {
            val connection = URL("http://127.0.0.1:$port/${server.token}/bundle").openConnection() as HttpURLConnection
            val reply = connection.run {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(TransferCodec.encode(bundle).toByteArray()) }
                assertEquals(200, responseCode)
                inputStream.bufferedReader().readText()
            }
            assertEquals(TransferResult(added = 1, existing = 1), TransferCodec.decodeResult(reply))
            assertEquals(bundle, received)
        } finally {
            server.stop()
        }
    }
}
