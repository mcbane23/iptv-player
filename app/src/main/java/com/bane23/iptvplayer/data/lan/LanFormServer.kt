package com.bane23.iptvplayer.data.lan

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Zajednički deo malih servera u lokalnoj mreži: prima veze na slobodnom portu i svaki zahtev
 * predaje funkciji [respond]. Radi samo dok je otvoren ekran koji ga je pokrenuo.
 */
abstract class LanFormServer {
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
                val response = if (request == null) badRequest() else respond(request)
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
    abstract fun respond(request: PairingRequest): ByteArray

    /** Odgovor na zahtev koji nije mogao da se pročita. */
    protected abstract fun badRequest(): ByteArray

    private companion object {
        const val READ_TIMEOUT_MS = 15_000
    }
}
