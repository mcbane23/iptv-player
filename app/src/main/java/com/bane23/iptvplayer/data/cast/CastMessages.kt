package com.bane23.iptvplayer.data.cast

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Odgovor TV-a na pitanje „ko si”: telefon tako proverava da je na adresi ova aplikacija. */
@Serializable
data class CastInfo(val app: String = APP, val name: String = "") {
    companion object {
        const val APP = "iptvplayer-cast"
    }
}

/** Kod sa ekrana TV-a koji korisnik prekucava na telefonu pri prvom povezivanju. */
@Serializable
data class CastPairConfirm(val code: String = "")

/** Ključ koji telefon pamti i šalje uz svaki sledeći zahtev. */
@Serializable
data class CastPairResult(val key: String = "")

/**
 * Šta TV treba da pusti. Telefon šalje gotove adrese strima, pa TV ne mora da ima istu listu.
 * [positionMs] je mesto od kog se nastavlja (film ili epizoda).
 */
@Serializable
data class CastPlayRequest(
    val title: String = "",
    val urls: List<String> = emptyList(),
    val userAgent: String? = null,
    val referrer: String? = null,
    val live: Boolean = true,
    val positionMs: Long = 0,
)

/** Komanda daljinskog. [valueMs] je pomak ili pozicija za premotavanje. */
@Serializable
data class CastCommand(val action: String = "", val valueMs: Long = 0) {
    companion object {
        const val TOGGLE = "toggle"
        const val SEEK_BY = "seekBy"
        const val SEEK_TO = "seekTo"
        const val VOLUME_UP = "volumeUp"
        const val VOLUME_DOWN = "volumeDown"
        const val STOP = "stop"
    }
}

/** Stanje puštanja na TV-u, koje telefon čita dok služi kao daljinski. */
@Serializable
data class CastStatus(
    val active: Boolean = false,
    val title: String = "",
    val live: Boolean = true,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

object CastCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> decode(text: String): T? = try {
        json.decodeFromString<T>(text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)
}
