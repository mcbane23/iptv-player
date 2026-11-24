package com.bane23.iptvplayer.data.weather

import android.os.SystemClock
import com.bane23.iptvplayer.data.prefs.SettingsStore
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Trenutna temperatura za čuvar ekrana, sa Open-Meteo (besplatno, bez naloga). Mesto je grad iz
 * Podešavanja, a dok ga nema, približno mesto po IP adresi. Sve se pamti u memoriji: mesto dok
 * se grad ne promeni, temperatura [REFRESH_MS], pa se servisi pitaju retko.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val settings: SettingsStore,
    private val client: OkHttpClient,
) {
    private val lock = Mutex()
    private var placeFor: String? = null
    private var place: WeatherPlace? = null
    private var placeAt = 0L
    private var temperature: Int? = null
    private var temperatureAt = 0L

    /** Temperatura u °C, zaokružena; null kad mesto ili vreme nisu dostupni (npr. nema interneta). */
    suspend fun currentTemperature(): Int? = lock.withLock {
        val city = settings.weatherCity.first().trim()
        val now = SystemClock.elapsedRealtime()
        if (city != placeFor) {
            placeFor = city
            place = null
            placeAt = 0L
            temperature = null
            temperatureAt = 0L
        }
        val fresh = temperature?.takeIf { now - temperatureAt < REFRESH_MS }
        if (fresh != null) return@withLock fresh
        // Neuspela pretraga mesta se ne ponavlja na svaki minut.
        if (place == null && (placeAt == 0L || now - placeAt >= REFRESH_MS)) {
            placeAt = now
            place = if (city.isEmpty()) locateByIp() else findCity(city)
        }
        val target = place ?: return@withLock null
        val value = fetch(target)
        if (value != null) {
            temperature = value
            temperatureAt = now
        } else if (now - temperatureAt >= STALE_MS) {
            // Bez interneta stara vrednost ostaje samo kratko, posle se prikazuje samo datum.
            temperature = null
        }
        temperature
    }

    private suspend fun fetch(place: WeatherPlace): Int? {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", place.latitude.toString())
            .addQueryParameter("longitude", place.longitude.toString())
            .addQueryParameter("current", "temperature_2m")
            .build()
        return get(url.toString(), ::parseTemperature)
    }

    private suspend fun findCity(city: String): WeatherPlace? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", Locale.getDefault().language)
            .build()
        return get(url.toString(), ::parseGeocoding)
    }

    private suspend fun locateByIp(): WeatherPlace? =
        IP_SERVICES.firstNotNullOfOrNull { url -> get(url, ::parseIpLocation) }

    /**
     * Odgovor pročitan sa [parse], ili null na bilo kakvu grešku (mreža, server, neočekivan JSON):
     * temperatura nije toliko bitna da bi se o grešci javljalo.
     */
    private suspend fun <T> get(url: String, parse: (String) -> T?): T? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.let(parse) else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val REFRESH_MS = 30 * 60_000L
        const val STALE_MS = 2 * 60 * 60_000L
        val IP_SERVICES = listOf("https://ipapi.co/json/", "https://ipwho.is/")
    }
}
