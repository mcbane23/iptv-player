package com.bane23.iptvplayer.data.lan

import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Ishod pokušaja da se doda lista poslata sa telefona. */
sealed interface PairingResult {
    data class Added(val playlistName: String) : PairingResult
    data class Failed(val message: String) : PairingResult
}

/**
 * Mali HTTP server koji radi samo dok je na TV-u otvoren ekran sa QR kodom. Telefon u istoj mreži
 * otvara adresu iz koda, dobija formu i šalje podatke liste.
 *
 * Adresa sadrži nasumičan [token]; zahtevi bez njega dobijaju 404, pa drugi uređaji u mreži ne
 * mogu da pogode stranicu. [onSubmit] se poziva na pozadinskoj niti i sme da traje.
 */
class PairingServer(
    private val texts: PairingTexts,
    private val onSubmit: (PairingSubmission) -> PairingResult,
    val token: String = newToken(),
) {
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    /** Pokreće server na slobodnom portu i vraća taj port. */
    @Synchronized
    fun start(): Int {
        serverSocket?.let { return it.localPort }
        val socket = ServerSocket(0)
        val pool = Executors.newCachedThreadPool { task -> Thread(task, "pairing-http").apply { isDaemon = true } }
        serverSocket = socket
        executor = pool
        pool.execute { acceptLoop(socket, pool) }
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
            // već zatvoren
        }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
    }

    private fun acceptLoop(socket: ServerSocket, pool: ExecutorService) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                return
            } catch (_: IOException) {
                return
            }
            try {
                pool.execute { serve(client) }
            } catch (_: RuntimeException) {
                client.close()
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use {
                it.soTimeout = READ_TIMEOUT_MS
                val request = PairingHttp.readRequest(it.getInputStream())
                val response = if (request == null) {
                    PairingHttp.htmlResponse(400, PairingPage.notFound(texts))
                } else {
                    respond(request)
                }
                it.getOutputStream().apply {
                    write(response)
                    flush()
                }
            }
        } catch (_: IOException) {
            // telefon je prekinuo vezu; nema šta da se radi
        }
    }

    /** Odgovor na jedan zahtev. Odvojeno od mreže, da može da se testira. */
    fun respond(request: PairingRequest): ByteArray {
        if (request.path != "/$token") return PairingHttp.htmlResponse(404, PairingPage.notFound(texts))
        return when (request.method) {
            "GET" -> PairingHttp.htmlResponse(200, PairingPage.form(texts))
            "POST" -> {
                val submission = PairingSubmission.fromForm(request.form)
                when (val result = onSubmit(submission)) {
                    is PairingResult.Added -> PairingHttp.htmlResponse(200, PairingPage.done(texts, result.playlistName))
                    is PairingResult.Failed ->
                        PairingHttp.htmlResponse(200, PairingPage.form(texts, error = result.message, previous = submission))
                }
            }
            else -> PairingHttp.htmlResponse(400, PairingPage.notFound(texts))
        }
    }

    companion object {
        private const val READ_TIMEOUT_MS = 15_000
        private const val TOKEN_LENGTH = 10
        private const val TOKEN_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

        fun newToken(): String {
            val random = SecureRandom()
            return (1..TOKEN_LENGTH).map { TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)] }.joinToString("")
        }

        /** Adresa uređaja u lokalnoj mreži (IPv4), ili null ako uređaj nije povezan na mrežu. */
        fun localAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.asSequence().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: SocketException) {
            null
        }
    }
}
