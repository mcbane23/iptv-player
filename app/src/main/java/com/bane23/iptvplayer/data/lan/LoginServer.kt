package com.bane23.iptvplayer.data.lan

/** Tekstovi stranice za prijavu koju telefon otvara. Dolaze iz `strings.xml`. */
data class LoginTexts(
    val title: String,
    val intro: String,
    val username: String,
    val password: String,
    val submit: String,
    val doneTitle: String,
    val doneText: String,
    val notFound: String,
)

/** Korisničko ime i lozinka poslati sa telefona. */
data class LoginSubmission(val username: String, val password: String) {
    companion object {
        const val FIELD_USERNAME = "username"
        const val FIELD_PASSWORD = "password"

        /** Korisničko ime se čisti od razmaka sa krajeva; lozinka ostaje tačno kako je ukucana. */
        fun fromForm(form: Map<String, String>) = LoginSubmission(
            username = form[FIELD_USERNAME].orEmpty().trim(),
            password = form[FIELD_PASSWORD].orEmpty(),
        )
    }
}

/**
 * Prijava na nalog preko telefona, po uzoru na [PairingServer]: TV prikaže QR kod sa adresom koja
 * sadrži nasumičan [token], a telefon u istoj mreži na toj adresi dobija formu za korisničko ime i
 * lozinku. [onSubmit] se poziva na pozadinskoj niti, sme da traje, i vraća poruku o grešci ili
 * null kada je prijava uspela.
 */
class LoginServer(
    private val texts: LoginTexts,
    private val onSubmit: (LoginSubmission) -> String?,
    val token: String = PairingServer.newToken(),
) : LanFormServer() {
    override fun badRequest(): ByteArray = notFound(400)

    override fun respond(request: PairingRequest): ByteArray {
        if (request.path != "/$token") return notFound(404)
        return when (request.method) {
            "GET" -> PairingHttp.htmlResponse(200, PairingPage.login(texts))
            "POST" -> {
                val submission = LoginSubmission.fromForm(request.form)
                val error = onSubmit(submission)
                val page = if (error == null) {
                    PairingPage.loginDone(texts, submission.username)
                } else {
                    PairingPage.login(texts, error = error, username = submission.username)
                }
                PairingHttp.htmlResponse(200, page)
            }
            else -> notFound(400)
        }
    }

    private fun notFound(status: Int) = PairingHttp.htmlResponse(status, PairingPage.notFound(texts.title, texts.notFound))
}
