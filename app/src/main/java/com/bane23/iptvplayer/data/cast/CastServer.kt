package com.bane23.iptvplayer.data.cast

import com.bane23.iptvplayer.data.lan.PairingHttp
import com.bane23.iptvplayer.data.lan.PairingRequest
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCodec
import com.bane23.iptvplayer.data.lan.TransferResult
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Ono što TV radi na zahteve telefona. Poziva se na pozadinskoj niti. */
interface CastHandler {
    fun info(): CastInfo

    /** Prikazuje kod na ekranu TV-a. */
    fun startPairing()

    /** Vraća ključ ako je [code] tačan, inače null. */
    fun confirmPairing(code: String): String?

    fun isKeyValid(key: String): Boolean

    fun play(request: CastPlayRequest)

    fun command(command: CastCommand)

    fun status(): CastStatus

    /** Primenjuje liste, omiljene, podešavanja i naloge poslate sa telefona. Sme da traje. */
    fun transfer(bundle: TransferBundle): TransferResult
}

/**
 * Mali HTTP server na TV-u preko kog telefon u istoj mreži pušta sadržaj i upravlja njime.
 * Radi samo dok je aplikacija na TV-u otvorena. Sve osim pitanja „ko si” i povezivanja traži ključ
 * dobijen prekucavanjem koda sa ekrana.
 */
class CastServer(private val handler: CastHandler) {
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    /**
     * Pokreće server i vraća njegov port. Prvo se proba stalni port [LanScan.PORT], da telefon nađe TV
     * i u mreži koja ne propušta oglašavanje; ako je zauzet, uzima se bilo koji slobodan.
     */
    @Synchronized
    fun start(): Int {
        serverSocket?.let { return it.localPort }
        val socket = try {
            ServerSocket(LanScan.PORT)
        } catch (_: IOException) {
            ServerSocket(0)
        }
        val pool = Executors.newCachedThreadPool { task -> Thread(task, "cast-http").apply { isDaemon = true } }
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
                val response = if (request == null) PairingHttp.jsonResponse(400, "{}") else respond(request)
                it.getOutputStream().apply {
                    write(response)
                    flush()
                }
            }
        } catch (_: IOException) {
            // telefon je prekinuo vezu
        }
    }

    /** Odgovor na jedan zahtev. Odvojeno od mreže, da može da se testira. */
    fun respond(request: PairingRequest): ByteArray {
        val path = request.path
        val post = request.method == "POST"
        return when {
            path == INFO_PATH && request.method == "GET" -> ok(CastCodec.encode(handler.info()))

            path == PAIR_PATH && post -> {
                handler.startPairing()
                ok("{}")
            }

            path == CONFIRM_PATH && post -> {
                val code = CastCodec.decode<CastPairConfirm>(request.body)?.code.orEmpty()
                val key = handler.confirmPairing(code)
                if (key == null) PairingHttp.jsonResponse(403, "{}") else ok(CastCodec.encode(CastPairResult(key)))
            }

            path.startsWith(KEY_PREFIX) -> respondWithKey(request, path.removePrefix(KEY_PREFIX))

            else -> PairingHttp.jsonResponse(404, "{}")
        }
    }

    private fun respondWithKey(request: PairingRequest, rest: String): ByteArray {
        val key = rest.substringBefore('/')
        val action = rest.substringAfter('/', "")
        if (key.isEmpty() || !handler.isKeyValid(key)) return PairingHttp.jsonResponse(403, "{}")
        val post = request.method == "POST"
        return when {
            action == "play" && post -> {
                val play = CastCodec.decode<CastPlayRequest>(request.body)
                val urls = play?.urls.orEmpty().filter { it.startsWith("http://") || it.startsWith("https://") }
                if (play == null || urls.isEmpty()) {
                    PairingHttp.jsonResponse(400, "{}")
                } else {
                    handler.play(play.copy(urls = urls))
                    ok("{}")
                }
            }

            action == "command" && post -> {
                val command = CastCodec.decode<CastCommand>(request.body)
                if (command == null || command.action.isEmpty()) {
                    PairingHttp.jsonResponse(400, "{}")
                } else {
                    handler.command(command)
                    ok(CastCodec.encode(handler.status()))
                }
            }

            action == "status" && request.method == "GET" -> ok(CastCodec.encode(handler.status()))

            action == BUNDLE_ACTION && post -> {
                val bundle = TransferCodec.decode(request.body)
                if (bundle == null) PairingHttp.jsonResponse(400, "{}") else ok(TransferCodec.encodeResult(handler.transfer(bundle)))
            }

            else -> PairingHttp.jsonResponse(404, "{}")
        }
    }

    private fun ok(json: String) = PairingHttp.jsonResponse(200, json)

    companion object {
        private const val READ_TIMEOUT_MS = 10_000

        const val INFO_PATH = "/cast/info"
        const val PAIR_PATH = "/cast/pair"
        const val CONFIRM_PATH = "/cast/confirm"
        const val KEY_PREFIX = "/cast/k/"

        /** Isti završetak puta kao kod prenosa preko QR koda, da telo sme da bude veliko. */
        const val BUNDLE_ACTION = "bundle"

        /** Vrsta servisa pod kojom se TV oglašava u lokalnoj mreži. */
        const val SERVICE_TYPE = "_iptvcast._tcp"
    }
}
