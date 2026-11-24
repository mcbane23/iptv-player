package com.bane23.iptvplayer.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherParsersTest {
    @Test
    fun temperatureIsRounded() {
        assertEquals(7, parseTemperature("""{"current":{"time":"2030-01-01T10:00","temperature_2m":7.4}}"""))
        assertEquals(-3, parseTemperature("""{"current":{"temperature_2m":-2.6},"extra":1}"""))
    }

    @Test
    fun missingTemperatureIsNull() {
        assertNull(parseTemperature("""{"current":{}}"""))
        assertNull(parseTemperature("""{}"""))
    }

    @Test
    fun geocodingTakesFirstResult() {
        val body = """{"results":[{"name":"Izmišljen Grad","latitude":45.5,"longitude":19.5},{"latitude":1.0,"longitude":2.0}]}"""
        assertEquals(WeatherPlace(45.5, 19.5), parseGeocoding(body))
        assertNull(parseGeocoding("""{"generationtime_ms":0.5}"""))
    }

    @Test
    fun ipLocationHonorsErrorFlags() {
        assertEquals(WeatherPlace(44.0, 20.0), parseIpLocation("""{"ip":"192.0.2.1","latitude":44.0,"longitude":20.0}"""))
        assertNull(parseIpLocation("""{"error":true,"reason":"RateLimited"}"""))
        assertNull(parseIpLocation("""{"success":false,"message":"Invalid"}"""))
        assertNull(parseIpLocation("""{"latitude":44.0}"""))
    }

    @Test
    fun temperatureTextUsesMinusSign() {
        assertEquals("7°", temperatureText(7))
        assertEquals("0°", temperatureText(0))
        assertEquals("−3°", temperatureText(-3))
    }
}
