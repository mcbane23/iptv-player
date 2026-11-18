package com.bane23.iptvplayer.data.cast

import com.bane23.iptvplayer.data.lan.PairingHttp
import com.bane23.iptvplayer.data.lan.PairingRequest
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCodec
import com.bane23.iptvplayer.data.lan.TransferPlaylist
import com.bane23.iptvplayer.data.lan.TransferResult
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastServerTest {
    private class FakeHandler : CastHandler {
        val pairing = CastPairing(newCode = { "4821" })
        var played: CastPlayRequest? = null
        val commands = mutableListOf<CastCommand>()

        override fun info() = CastInfo(name = "Dnevna soba")

        override fun startPairing() {
            pairing.begin()
        }

        override fun confirmPairing(code: String): String? = if (pairing.confirm(code)) KEY else null

        override fun isKeyValid(key: String) = key == KEY

        override fun play(request: CastPlayRequest) {
            played = request
        }

        override fun command(command: CastCommand) {
            commands += command
        }

        override fun status() = CastStatus(active = played != null, title = played?.title.orEmpty())

        var received: TransferBundle? = null

        override fun transfer(bundle: TransferBundle): TransferResult {
            received = bundle
            return TransferResult(added = bundle.playlists.size)
        }
    }

    private val handler = FakeHandler()
    private val server = CastServer(handler)

    private fun call(method: String, path: String, body: String = ""): Pair<Int, String> {
        val text = String(server.respond(PairingRequest(method, path, emptyMap(), body = body)), Charsets.UTF_8)
        val status = text.substringAfter(' ').substringBefore(' ').toInt()
        return status to text.substringAfter("\r\n\r\n")
    }

    @Test
    fun `info is public and names the app`() {
        val (status, body) = call("GET", "/cast/info")
        assertEquals(200, status)
        val info = CastCodec.decode<CastInfo>(body)!!
        assertEquals(CastInfo.APP, info.app)
        assertEquals("Dnevna soba", info.name)
    }

    @Test
    fun `pairing gives the key only for the code shown on the tv`() {
        assertEquals(200, call("POST", "/cast/pair").first)
        assertEquals(403, call("POST", "/cast/confirm", """{"code":"0000"}""").first)
        val (status, body) = call("POST", "/cast/confirm", """{"code":"4821"}""")
        assertEquals(200, status)
        assertEquals(KEY, CastCodec.decode<CastPairResult>(body)!!.key)
        // Kod se troši: drugi put više ne važi.
        assertEquals(403, call("POST", "/cast/confirm", """{"code":"4821"}""").first)
    }

    @Test
    fun `play needs the key and a http address`() {
        val play = """{"title":"Probni kanal","urls":["http://example.invalid/live/1.ts"],"live":true}"""
        assertEquals(403, call("POST", "/cast/k/wrong/play", play).first)
        assertNull(handler.played)
        assertEquals(400, call("POST", "/cast/k/$KEY/play", """{"title":"x","urls":["file:///sdcard/a"]}""").first)
        assertEquals(400, call("POST", "/cast/k/$KEY/play", "not json").first)
        assertNull(handler.played)
        assertEquals(200, call("POST", "/cast/k/$KEY/play", play).first)
        assertEquals("Probni kanal", handler.played?.title)
        assertTrue(handler.played!!.live)
    }

    @Test
    fun `addresses that are not http are dropped from a play request`() {
        val play = """{"title":"x","urls":["content://media/1","https://example.invalid/a.m3u8"],"live":true}"""
        assertEquals(200, call("POST", "/cast/k/$KEY/play", play).first)
        assertEquals(listOf("https://example.invalid/a.m3u8"), handler.played?.urls)
    }

    @Test
    fun `command is passed on and answers with the status`() {
        call("POST", "/cast/k/$KEY/play", """{"title":"Probni film","urls":["http://example.invalid/movie/7.mkv"],"live":false}""")
        val (status, body) = call("POST", "/cast/k/$KEY/command", """{"action":"seekBy","valueMs":-10000}""")
        assertEquals(200, status)
        assertEquals(CastCommand(CastCommand.SEEK_BY, -10_000), handler.commands.single())
        assertEquals("Probni film", CastCodec.decode<CastStatus>(body)!!.title)
        assertEquals(403, call("GET", "/cast/k/other/status").first)
        assertEquals(200, call("GET", "/cast/k/$KEY/status").first)
        assertEquals(404, call("GET", "/cast/k/$KEY/unknown").first)
        assertEquals(404, call("GET", "/elsewhere").first)
    }

    @Test
    fun `bundle needs the key and is handed to the tv`() {
        val bundle = TransferBundle(
            playlists = listOf(TransferPlaylist(type = TransferPlaylist.TYPE_M3U, name = "Proba", source = "http://example.invalid/l.m3u")),
        )
        val body = TransferCodec.encode(bundle)
        assertEquals(403, call("POST", "/cast/k/other/bundle", body).first)
        assertNull(handler.received)
        assertEquals(400, call("POST", "/cast/k/$KEY/bundle", "nije paket").first)
        assertEquals(404, call("GET", "/cast/k/$KEY/bundle").first)
        val (status, answer) = call("POST", "/cast/k/$KEY/bundle", body)
        assertEquals(200, status)
        assertEquals(bundle, handler.received)
        assertEquals(1, TransferCodec.decodeResult(answer)?.added)
    }

    @Test
    fun `a large bundle sent with the key is read whole`() {
        val body = "x".repeat(100_000)
        val raw = "POST /cast/k/$KEY/bundle HTTP/1.1\r\nContent-Length: ${body.length}\r\n\r\n$body"
        assertEquals(body, PairingHttp.readRequest(ByteArrayInputStream(raw.toByteArray()))?.body)
    }

    @Test
    fun `json body of a cast request is kept raw`() {
        val body = """{"code":"4821"}"""
        val raw = "POST /cast/confirm HTTP/1.1\r\nContent-Length: ${body.length}\r\n\r\n$body"
        val parsed = PairingHttp.readRequest(ByteArrayInputStream(raw.toByteArray()))!!
        assertEquals(body, parsed.body)
        assertTrue(parsed.form.isEmpty())
    }

    @Test
    fun `pairing code expires and stops after too many wrong tries`() {
        var time = 0L
        val pairing = CastPairing(now = { time }, newCode = { "1234" })
        assertNull(pairing.current())
        assertFalse(pairing.confirm("1234"))
        pairing.begin()
        assertEquals("1234", pairing.current())
        time = CastPairing.VALID_MS + 1
        assertFalse(pairing.confirm("1234"))

        time = 0
        pairing.begin()
        repeat(CastPairing.MAX_ATTEMPTS) { assertFalse(pairing.confirm("9999")) }
        assertFalse(pairing.confirm("1234"))
    }

    private companion object {
        const val KEY = "testkey"
    }
}
