package com.bane23.iptvplayer.data.weather

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON servisa za vreme i lokaciju: nepoznata polja se preskaču. */
internal val WeatherJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Mesto za koje se traži temperatura. */
data class WeatherPlace(val latitude: Double, val longitude: Double)

/** Odgovor Open-Meteo prognoze; koristi se samo trenutna temperatura. */
@Serializable
internal data class ForecastResponse(val current: Current? = null) {
    @Serializable
    data class Current(@SerialName("temperature_2m") val temperature: Double? = null)
}

/** Odgovor Open-Meteo pretrage gradova. */
@Serializable
internal data class GeocodingResponse(val results: List<Result> = emptyList()) {
    @Serializable
    data class Result(val latitude: Double? = null, val longitude: Double? = null)
}

/** Trenutna temperatura zaokružena na ceo stepen, ili null kad je u odgovoru nema. */
internal fun parseTemperature(body: String): Int? {
    val value = WeatherJson.decodeFromString<ForecastResponse>(body).current?.temperature ?: return null
    if (value.isNaN() || value.isInfinite()) return null
    return value.roundToInt()
}

/** Odgovor servisa koji po IP adresi daje približno mesto (ipapi.co i ipwho.is imaju ista polja). */
@Serializable
internal data class IpLocationResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val error: Boolean = false,
    val success: Boolean = true,
)

/** Mesto po IP adresi, ili null kad servis javi grešku ili nema koordinata. */
internal fun parseIpLocation(body: String): WeatherPlace? {
    val response = WeatherJson.decodeFromString<IpLocationResponse>(body)
    if (response.error || !response.success) return null
    return WeatherPlace(response.latitude ?: return null, response.longitude ?: return null)
}

/** Prvo mesto iz pretrage grada, ili null kad grad nije pronađen. */
internal fun parseGeocoding(body: String): WeatherPlace? {
    val first = WeatherJson.decodeFromString<GeocodingResponse>(body).results.firstOrNull() ?: return null
    return WeatherPlace(first.latitude ?: return null, first.longitude ?: return null)
}

/** Tekst temperature za ekran, npr. „7°” ili „−3°” (pravi znak minus). */
fun temperatureText(celsius: Int): String = if (celsius < 0) "−${-celsius}°" else "$celsius°"
