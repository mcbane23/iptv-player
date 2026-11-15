package com.bane23.iptvplayer.data.lan

/** Tekstovi stranice koju telefon otvara. Dolaze iz `strings.xml`, da stranica bude na jeziku uređaja. */
data class PairingTexts(
    val title: String,
    val intro: String,
    val xtreamHeading: String,
    val m3uHeading: String,
    val name: String,
    val server: String,
    val username: String,
    val password: String,
    val m3uUrl: String,
    val epgUrl: String,
    val submit: String,
    val wait: String,
    val doneTitle: String,
    val doneText: String,
    val notFound: String,
    /** Odeljak za slanje svih lista iz aplikacije na telefonu. */
    val appHeading: String = "",
    val appText: String = "",
    val appButton: String = "",
)

/** Šta je korisnik poslao sa telefona. */
data class PairingSubmission(
    val xtream: Boolean,
    val name: String,
    val server: String,
    val username: String,
    val password: String,
    val url: String,
    val epgUrl: String,
) {
    companion object {
        const val FIELD_KIND = "kind"
        const val KIND_XTREAM = "xtream"
        const val KIND_M3U = "m3u"
        const val FIELD_NAME = "name"
        const val FIELD_SERVER = "server"
        const val FIELD_USERNAME = "username"
        const val FIELD_PASSWORD = "password"
        const val FIELD_URL = "url"
        const val FIELD_EPG = "epg"

        /** Adrese, korisničko ime i lozinka ne sadrže razmake, pa se uklanjaju (tastature ih rado ubace). */
        fun fromForm(form: Map<String, String>): PairingSubmission {
            fun compact(field: String) = form[field].orEmpty().filterNot { it.isWhitespace() }
            return PairingSubmission(
                xtream = form[FIELD_KIND] == KIND_XTREAM,
                name = form[FIELD_NAME].orEmpty().trim(),
                server = compact(FIELD_SERVER),
                username = compact(FIELD_USERNAME),
                password = compact(FIELD_PASSWORD),
                url = compact(FIELD_URL),
                epgUrl = compact(FIELD_EPG),
            )
        }
    }
}

/** HTML stranice za telefon. Bez skripti i spoljnih fajlova, da radi u svakom pregledaču. */
object PairingPage {
    /**
     * Stranica sa dve forme: Xtream nalog i M3U link. [error] se prikazuje na vrhu, a [previous]
     * vraća ono što je već uneto (osim lozinke), da se posle greške ne kuca sve ponovo. Ako je dat
     * [appLink], na vrhu je i dugme koje otvara aplikaciju na telefonu da pošalje sve liste odjednom.
     */
    fun form(
        texts: PairingTexts,
        error: String? = null,
        previous: PairingSubmission? = null,
        appLink: String? = null,
    ): String {
        val name = previous?.name.orEmpty()
        val epg = previous?.epgUrl.orEmpty()
        return page(texts.title) {
            append("<h1>").append(escape(texts.title)).append("</h1>")
            append("<p>").append(escape(texts.intro)).append("</p>")
            if (error != null) append("<p class=\"error\">").append(escape(error).replace("\n", "<br>")).append("</p>")

            if (appLink != null) {
                append("<div class=\"card\"><h2>").append(escape(texts.appHeading)).append("</h2>")
                append("<p class=\"note\">").append(escape(texts.appText)).append("</p>")
                append("<a class=\"button\" href=\"").append(escape(appLink)).append("\">")
                append(escape(texts.appButton)).append("</a></div>")
            }

            append("<form method=\"post\"><h2>").append(escape(texts.xtreamHeading)).append("</h2>")
            hidden(PairingSubmission.FIELD_KIND, PairingSubmission.KIND_XTREAM)
            field(texts.name, PairingSubmission.FIELD_NAME, name)
            field(texts.server, PairingSubmission.FIELD_SERVER, previous?.server.orEmpty(), type = "url")
            field(texts.username, PairingSubmission.FIELD_USERNAME, previous?.username.orEmpty())
            field(texts.password, PairingSubmission.FIELD_PASSWORD, "", type = "password")
            field(texts.epgUrl, PairingSubmission.FIELD_EPG, epg, type = "url")
            submit(texts)
            append("</form>")

            append("<form method=\"post\"><h2>").append(escape(texts.m3uHeading)).append("</h2>")
            hidden(PairingSubmission.FIELD_KIND, PairingSubmission.KIND_M3U)
            field(texts.name, PairingSubmission.FIELD_NAME, name)
            field(texts.m3uUrl, PairingSubmission.FIELD_URL, previous?.url.orEmpty(), type = "url")
            field(texts.epgUrl, PairingSubmission.FIELD_EPG, epg, type = "url")
            submit(texts)
            append("</form>")
        }
    }

    /** Potvrda da je lista dodata na TV. */
    fun done(texts: PairingTexts, playlistName: String): String = page(texts.doneTitle) {
        append("<h1>").append(escape(texts.doneTitle)).append("</h1>")
        append("<p><b>").append(escape(playlistName)).append("</b></p>")
        append("<p>").append(escape(texts.doneText)).append("</p>")
    }

    /** Odgovor na adresu koja nije ona iz QR koda. */
    fun notFound(texts: PairingTexts): String = page(texts.title) {
        append("<p>").append(escape(texts.notFound)).append("</p>")
    }

    fun escape(text: String): String = buildString(text.length) {
        for (char in text) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    private fun page(title: String, body: StringBuilder.() -> Unit): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>").append(escape(title)).append("</title><style>").append(STYLE).append("</style></head><body>")
        body()
        append("</body></html>")
    }

    private fun StringBuilder.hidden(name: String, value: String) {
        append("<input type=\"hidden\" name=\"").append(name).append("\" value=\"").append(escape(value)).append("\">")
    }

    private fun StringBuilder.field(label: String, name: String, value: String, type: String = "text") {
        append("<label>").append(escape(label))
        append("<input type=\"").append(type).append("\" name=\"").append(name).append("\" value=\"")
        append(escape(value))
        // Telefon ne sme da ispravlja ni da piše veliko slovo: adrese se unose tačno.
        append("\" autocapitalize=\"none\" autocorrect=\"off\" autocomplete=\"off\" spellcheck=\"false\"></label>")
    }

    private fun StringBuilder.submit(texts: PairingTexts) {
        append("<button type=\"submit\">").append(escape(texts.submit)).append("</button>")
        append("<p class=\"note\">").append(escape(texts.wait)).append("</p>")
    }

    private const val STYLE =
        "body{font-family:sans-serif;margin:0 auto;max-width:520px;padding:16px;background:#12151b;color:#e8eaed}" +
            "h1{font-size:22px}h2{font-size:18px;margin:0 0 8px}" +
            "form,.card{background:#1c2029;border-radius:12px;padding:16px;margin:16px 0}" +
            "a.button{display:block;text-align:center;text-decoration:none;margin-top:12px}" +
            "label{display:block;margin:10px 0;font-size:14px;color:#aab}" +
            "input{display:block;width:100%;box-sizing:border-box;margin-top:4px;padding:12px;font-size:16px;" +
            "border-radius:8px;border:1px solid #445;background:#0e1116;color:#fff}" +
            "button,a.button{width:100%;box-sizing:border-box;padding:14px;font-size:16px;border:0;border-radius:8px;background:#4c8dff;color:#fff}" +
            ".error{background:#5a1f24;border-radius:8px;padding:12px}.note{font-size:13px;color:#99a;margin:8px 0 0}"
}
