package com.bane23.iptvplayer.data.lan

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private val texts = PairingTexts(
        title = "Add playlist", intro = "Intro", xtreamHeading = "Xtream", m3uHeading = "M3U",
        name = "Name", server = "Server", username = "User", password = "Pass", m3uUrl = "Link",
        epgUrl = "EPG", submit = "Send", wait = "Wait", doneTitle = "Done", doneText = "Look at the TV",
        notFound = "Scan again",
    )

    private fun request(raw: String) = PairingHttp.readRequest(ByteArrayInputStream(raw.toByteArray()))

    @Test
    fun `reads a get request and drops the query`() {
        val parsed = request("GET /abc123?x=1 HTTP/1.1\r\nHost: tv.invalid\r\n\r\n")!!
        assertEquals("GET", parsed.method)
        assertEquals("/abc123", parsed.path)
        assertTrue(parsed.form.isEmpty())
    }

    @Test
    fun `reads a posted form with encoded characters`() {
        val body = "kind=xtream&name=Ku%C4%87na+lista&server=http%3A%2F%2Fexample.invalid%3A8080&username=u%26me&password=p%3Dw"
        val parsed = request("POST /tok HTTP/1.1\r\ncontent-length: ${body.length}\r\n\r\n$body")!!
        assertEquals("POST", parsed.method)
        val submission = PairingSubmission.fromForm(parsed.form)
        assertTrue(submission.xtream)
        assertEquals("Kućna lista", submission.name)
        assertEquals("http://example.invalid:8080", submission.server)
        assertEquals("u&me", submission.username)
        assertEquals("p=w", submission.password)
    }

    @Test
    fun `rejects broken and oversized requests`() {
        assertNull(request(""))
        assertNull(request("GET /abc HTTP/1.1\r\nHost: tv.invalid\r\n"))
        assertNull(request("POST /abc HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n"))
        assertNull(request("POST /abc HTTP/1.1\r\nContent-Length: 20\r\n\r\nshort"))
    }

    @Test
    fun `submission strips whitespace from addresses and credentials but keeps it in the name`() {
        val submission = PairingSubmission.fromForm(
            mapOf(
                "kind" to "m3u",
                "name" to "  My list ",
                "url" to " http://Example .invalid/list.m3u ",
                "username" to "us er",
            ),
        )
        assertFalse(submission.xtream)
        assertEquals("My list", submission.name)
        assertEquals("http://Example.invalid/list.m3u", submission.url)
        assertEquals("user", submission.username)
    }

    @Test
    fun `page escapes user input and never echoes the password`() {
        val previous = PairingSubmission(true, "<b>x</b>", "http://a.invalid/\"q", "user", "secret-pass", "", "")
        val html = PairingPage.form(texts, error = "Bad <thing>", previous = previous)
        assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt;"))
        assertTrue(html.contains("Bad &lt;thing&gt;"))
        assertTrue(html.contains("http://a.invalid/&quot;q"))
        assertFalse(html.contains("<b>x</b>"))
        assertFalse(html.contains("secret-pass"))
    }

    @Test
    fun `only the address with the token is served`() {
        val server = PairingServer(texts, onSubmit = { PairingResult.Added(it.name) }, token = "tok123")
        val wrong = String(server.respond(PairingRequest("GET", "/other", emptyMap())))
        assertTrue(wrong.startsWith("HTTP/1.1 404"))
        val right = String(server.respond(PairingRequest("GET", "/tok123", emptyMap())))
        assertTrue(right.startsWith("HTTP/1.1 200"))
        assertTrue(right.contains("<form"))
    }

    @Test
    fun `failed submission shows the form again with the error`() {
        val server = PairingServer(texts, onSubmit = { PairingResult.Failed("Wrong password") }, token = "tok123")
        val form = mapOf("kind" to "xtream", "server" to "http://example.invalid", "username" to "user")
        val html = String(server.respond(PairingRequest("POST", "/tok123", form)))
        assertTrue(html.contains("Wrong password"))
        assertTrue(html.contains("value=\"http://example.invalid\""))
    }

    @Test
    fun `serves the form and accepts a submission over a real socket`() {
        var received: PairingSubmission? = null
        val server = PairingServer(
            texts,
            onSubmit = {
                received = it
                PairingResult.Added("Test list")
            },
        )
        val port = server.start()
        try {
            val base = "http://127.0.0.1:$port/${server.token}"
            val page = (URL(base).openConnection() as HttpURLConnection).run {
                assertEquals(200, responseCode)
                inputStream.bufferedReader().readText()
            }
            assertTrue(page.contains("<form"))

            val done = (URL(base).openConnection() as HttpURLConnection).run {
                requestMethod = "POST"
                doOutput = true
                outputStream.use { it.write("kind=m3u&name=Test+list&url=http%3A%2F%2Fexample.invalid%2Fa.m3u".toByteArray()) }
                assertEquals(200, responseCode)
                inputStream.bufferedReader().readText()
            }
            assertTrue(done.contains("Test list"))
            assertEquals("http://example.invalid/a.m3u", received?.url)

            val missing = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
            assertEquals(404, missing.responseCode)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `tokens are random and url safe`() {
        val first = PairingServer.newToken()
        val second = PairingServer.newToken()
        assertTrue(first.all { it.isLetterOrDigit() })
        assertTrue(first != second)
    }
}
