package com.bane23.iptvplayer.data.lan

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Sve što telefon šalje TV-u odjednom: liste sa omiljenim kanalima, filmovima i serijama i uređenjem
 * kategorija, podešavanja i nalozi. Putuje samo kroz lokalnu mrežu, pravo sa uređaja na uređaj.
 */
@Serializable
data class TransferBundle(
    val version: Int = VERSION,
    val playlists: List<TransferPlaylist> = emptyList(),
    val settings: TransferSettings? = null,
    /** Nalozi za Trakt i OpenSubtitles; starija verzija na TV-u ih samo preskače. */
    val accounts: TransferAccounts? = null,
    /** Zapis PIN-a roditeljske zaštite (otisak, ne sam PIN); TV ga uzima samo ako nema svoj. */
    val pinRecord: String? = null,
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
data class TransferPlaylist(
    /** [TYPE_XTREAM] ili [TYPE_M3U]. Liste iz fajla se ne prenose, jer je fajl na telefonu. */
    val type: String,
    val name: String,
    /** Adresa servera (Xtream) ili link ka listi (M3U). */
    val source: String,
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
    val epgUrl: String = "",
    val epgShiftHours: Int = 0,
    /** Stabilni ključevi omiljenih kanala; isti su na oba uređaja jer zavise samo od liste. */
    val favorites: List<String> = emptyList(),
    /** Samo kategorije koje je korisnik sakrio ili pomerio. */
    val categories: List<TransferCategory> = emptyList(),
    /** Omiljeni filmovi i serije. */
    val vodFavorites: List<TransferVodFavorite> = emptyList(),
    /**
     * Sakrivene kategorije i redosled kategorija filmova i serija; null ako ih telefon nije poslao
     * (starija verzija), pa na TV-u ostaje kako jeste.
     */
    val vodCategories: TransferVodCategories? = null,
    /** Kategorije zaključane roditeljskom zaštitom; sam PIN se ne prenosi. Null kad šalje starija verzija. */
    val locked: TransferLocks? = null,
    /** Da li je ovo lista koja je na telefonu izabrana kao aktivna. */
    val active: Boolean = false,
) {
    companion object {
        const val TYPE_XTREAM = "xtream"
        const val TYPE_M3U = "m3u"
    }
}

@Serializable
data class TransferCategory(
    val name: String,
    /** Vrsta kategorije, kao naziv iz `CategoryType`. */
    val type: String,
    val hidden: Boolean = false,
    val order: Int? = null,
)

/** Omiljeni film ili serija; [itemId] daje server, pa je isti na oba uređaja. */
@Serializable
data class TransferVodFavorite(
    /** "movie" ili "series". */
    val kind: String,
    val itemId: Long,
    val name: String,
    val poster: String? = null,
    val extension: String? = null,
)

/** Zaključane kategorije: kanala po nazivu, filmova i serija po ID-ju sa servera. */
@Serializable
data class TransferLocks(
    val live: List<String> = emptyList(),
    val movies: List<String> = emptyList(),
    val series: List<String> = emptyList(),
)

/** Uređenje kategorija filmova i serija, po ID-jevima kategorija sa servera. */
@Serializable
data class TransferVodCategories(
    val hiddenMovies: List<String> = emptyList(),
    val hiddenSeries: List<String> = emptyList(),
    val orderMovies: List<String> = emptyList(),
    val orderSeries: List<String> = emptyList(),
)

/** Podešavanja koja imaju smisla na oba uređaja. Dekoder se ne prenosi, jer zavisi od uređaja. */
@Serializable
data class TransferSettings(
    val resumeLastChannel: Boolean = false,
    val buffer: String = "",
    val audioLanguage: String = "",
    val subtitleLanguage: String = "",
    // Sve ispod je null kada šalje starija verzija; TV tada ne dira svoje podešavanje.
    val seekStepSeconds: Int? = null,
    val subtitleBackground: Boolean? = null,
    val subtitleSize: String? = null,
    val subtitlePosition: String? = null,
    val autoRefreshPlaylists: Boolean? = null,
    val accentColor: String? = null,
    val vodSortMovies: String? = null,
    val vodSortSeries: String? = null,
)

@Serializable
data class TransferAccounts(
    val openSubtitles: TransferLogin? = null,
    val trakt: TransferTraktLogin? = null,
)

/** Korisničko ime i lozinka; TV se njima prijavljuje sam. */
@Serializable
data class TransferLogin(val username: String, val password: String)

/** Posebna Trakt prijava koju je telefon dobio za TV (ne ona kojom je sam prijavljen). */
@Serializable
data class TransferTraktLogin(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val createdAt: Long,
)

/** Šta je TV uradio sa primljenim paketom. */
@Serializable
data class TransferResult(
    /** Nove liste koje su dodate. */
    val added: Int = 0,
    /** Liste koje je TV već imao; njima su samo dopunjeni omiljeni kanali. */
    val existing: Int = 0,
    /** Liste koje nisu mogle da se učitaju. */
    val failed: Int = 0,
    /** Da li je nalog na TV-u povezan; null ako nije ni poslat (ili je na TV-u starija verzija). */
    val openSubtitles: Boolean? = null,
    val trakt: Boolean? = null,
)

object TransferCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(bundle: TransferBundle): String = json.encodeToString(TransferBundle.serializer(), bundle)

    /** Vraća null ako tekst nije ispravan paket ili je iz novije, nepoznate verzije. */
    fun decode(text: String): TransferBundle? = try {
        json.decodeFromString(TransferBundle.serializer(), text).takeIf { it.version in 1..TransferBundle.VERSION }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun encodeResult(result: TransferResult): String = json.encodeToString(TransferResult.serializer(), result)

    fun decodeResult(text: String): TransferResult? = try {
        json.decodeFromString(TransferResult.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Adresa TV-a na koju telefon šalje paket. Paket sadrži lozinke, pa se prihvata samo adresa
 * uređaja u lokalnoj mreži, u obliku koji pravi ekran sa QR kodom: `http://192.168.1.20:40123/token`.
 */
object TransferTarget {
    const val SCHEME = "iptvplayer"
    const val HOST = "send"
    const val PARAM_TO = "to"
    const val BUNDLE_PATH = "/bundle"

    private val TOKEN_PATH = Regex("/[a-z0-9]{6,40}")
    private val HOST_PORT = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3}):(\d{1,5})""")

    /** Vraća adresu ako je bezbedna za slanje, inače null. */
    fun validate(target: String?): String? {
        if (target.isNullOrBlank() || target.length > 200) return null
        val uri = try {
            URI(target)
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.scheme != "http" || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.rawPath ?: return null
        if (!TOKEN_PATH.matches(path)) return null
        val authority = uri.rawAuthority ?: return null
        if (!isLocalHostPort(authority)) return null
        return "http://$authority$path"
    }

    /** `adresa:port` uređaja u privatnoj mreži (10.x, 172.16–31.x, 192.168.x). */
    fun isLocalHostPort(hostPort: String): Boolean {
        val match = HOST_PORT.matchEntire(hostPort) ?: return false
        val parts = match.groupValues.drop(1).map { it.toInt() }
        if (parts.take(4).any { it > 255 } || parts[4] !in 1..65535) return false
        val (a, b) = parts
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    /** Link koji na telefonu otvara aplikaciju sa adresom TV-a. */
    fun appLink(target: String): String = "$SCHEME://$HOST?$PARAM_TO=" + URLEncoder.encode(target, "UTF-8")

    /** Samo `adresa:port`, za prikaz korisniku. */
    fun displayAddress(target: String): String = target.removePrefix("http://").substringBefore('/')
}
