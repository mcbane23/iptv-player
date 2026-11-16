package com.bane23.iptvplayer.data.lan

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.security.SecureRandom

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
    /** Prima paket sa svim listama iz aplikacije na telefonu. Null = prenos nije podržan. */
    private val onBundle: ((TransferBundle) -> TransferResult)? = null,
) : LanFormServer() {
    override fun badRequest(): ByteArray = PairingHttp.htmlResponse(400, PairingPage.notFound(texts))

    /** Odgovor na jedan zahtev. Odvojeno od mreže, da može da se testira. */
    override fun respond(request: PairingRequest): ByteArray {
        if (request.path == "/$token${TransferTarget.BUNDLE_PATH}") return respondToBundle(request)
        if (request.path != "/$token") return PairingHttp.htmlResponse(404, PairingPage.notFound(texts))
        // Link ka aplikaciji se nudi samo ako je telefon došao preko adrese u lokalnoj mreži.
        val appLink = request.host
            .takeIf { onBundle != null && TransferTarget.isLocalHostPort(it) }
            ?.let { TransferTarget.appLink("http://$it/$token") }
        return when (request.method) {
            "GET" -> PairingHttp.htmlResponse(200, PairingPage.form(texts, appLink = appLink))
            "POST" -> {
                val submission = PairingSubmission.fromForm(request.form)
                when (val result = onSubmit(submission)) {
                    is PairingResult.Added -> PairingHttp.htmlResponse(200, PairingPage.done(texts, result.playlistName))
                    is PairingResult.Failed ->
                        PairingHttp.htmlResponse(
                            200,
                            PairingPage.form(texts, error = result.message, previous = submission, appLink = appLink),
                        )
                }
            }
            else -> PairingHttp.htmlResponse(400, PairingPage.notFound(texts))
        }
    }

    private fun respondToBundle(request: PairingRequest): ByteArray {
        val handler = onBundle
        if (handler == null || request.method != "POST") return PairingHttp.jsonResponse(404, "{}")
        val bundle = TransferCodec.decode(request.body) ?: return PairingHttp.jsonResponse(400, "{}")
        return PairingHttp.jsonResponse(200, TransferCodec.encodeResult(handler(bundle)))
    }

    companion object {
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
