package com.bane23.iptvplayer.data.lan

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginServerTest {
    private val texts = LoginTexts(
        title = "Sign in", intro = "Intro", username = "User", password = "Pass", submit = "Send",
        doneTitle = "Signed in", doneText = "Look at the TV", notFound = "Scan again",
    )

    @Test
    fun `submission trims the username and keeps the password as typed`() {
        val submission = LoginSubmission.fromForm(mapOf("username" to "  some.user ", "password" to " pass word "))
        assertEquals("some.user", submission.username)
        assertEquals(" pass word ", submission.password)
    }

    @Test
    fun `only the address with the token is served`() {
        val server = LoginServer(texts, onSubmit = { null }, token = "tok123")
        assertTrue(String(server.respond(PairingRequest("GET", "/other", emptyMap()))).startsWith("HTTP/1.1 404"))
        assertTrue(String(server.respond(PairingRequest("POST", "/", mapOf("username" to "u")))).startsWith("HTTP/1.1 404"))
        val right = String(server.respond(PairingRequest("GET", "/tok123", emptyMap())))
        assertTrue(right.startsWith("HTTP/1.1 200"))
        assertTrue(right.contains("type=\"password\""))
    }

    @Test
    fun `failed sign in shows the form again with the error and without the password`() {
        val server = LoginServer(texts, onSubmit = { "Wrong <password>" }, token = "tok123")
        val form = mapOf("username" to "some\"user", "password" to "secret-pass")
        val html = String(server.respond(PairingRequest("POST", "/tok123", form)))
        assertTrue(html.contains("Wrong &lt;password&gt;"))
        assertTrue(html.contains("value=\"some&quot;user\""))
        assertFalse(html.contains("secret-pass"))
    }

    @Test
    fun `signs in over a real socket`() {
        var received: LoginSubmission? = null
        val server = LoginServer(
            texts,
            onSubmit = {
                received = it
                null
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
                outputStream.use { it.write("username=some.user&password=p%26w+1".toByteArray()) }
                assertEquals(200, responseCode)
                inputStream.bufferedReader().readText()
            }
            assertTrue(done.contains("Signed in"))
            assertTrue(done.contains("some.user"))
            assertFalse(done.contains("p&amp;w"))
            assertEquals(LoginSubmission("some.user", "p&w 1"), received)
        } finally {
            server.stop()
        }
    }
}
