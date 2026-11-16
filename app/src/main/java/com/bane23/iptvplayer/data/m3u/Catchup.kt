package com.bane23.iptvplayer.data.m3u

import java.util.Calendar
import java.util.TimeZone

/**
 * Arhiva (catch-up) za M3U liste: od adrese kanala i atributa `catchup` / `catchup-source` pravi
 * adresu za gledanje emisije koja je već prošla. Podržani su najčešći oblici: `default` (gotov
 * šablon), `append` (šablon se dodaje na adresu kanala), `shift` i `flussonic`.
 */
object Catchup {
    /** Da li iz ovih atributa može da se napravi adresa arhive. */
    fun isSupported(type: String?, source: String?, streamUrl: String?): Boolean = when (kind(type, source)) {
        Kind.DEFAULT -> !source.isNullOrBlank()
        Kind.APPEND -> !source.isNullOrBlank() && !streamUrl.isNullOrBlank()
        Kind.SHIFT -> !streamUrl.isNullOrBlank()
        Kind.FLUSSONIC -> streamUrl != null && flussonic(streamUrl, 0, 0) != null
        null -> false
    }

    /** Adresa emisije koja je trajala od [startMs] do [stopMs], ili null ako oblik nije podržan. */
    fun url(type: String?, source: String?, streamUrl: String?, startMs: Long, stopMs: Long, nowMs: Long): String? {
        if (!isSupported(type, source, streamUrl) || stopMs <= startMs) return null
        val start = startMs / 1000
        val stop = stopMs / 1000
        val now = nowMs / 1000
        return when (kind(type, source)) {
            Kind.DEFAULT -> fill(source.orEmpty().trim(), start, stop, now)
            Kind.APPEND -> streamUrl.orEmpty().trim() + fill(source.orEmpty().trim(), start, stop, now)
            Kind.SHIFT -> {
                val base = streamUrl.orEmpty().trim()
                base + (if ('?' in base) "&" else "?") + "utc=$start&lutc=$now"
            }
            Kind.FLUSSONIC -> flussonic(streamUrl.orEmpty().trim(), start, stop - start)
            null -> null
        }
    }

    private enum class Kind { DEFAULT, APPEND, SHIFT, FLUSSONIC }

    private fun kind(type: String?, source: String?): Kind? = when (type?.trim()?.lowercase()) {
        "default" -> Kind.DEFAULT
        "append" -> Kind.APPEND
        "shift", "timeshift" -> Kind.SHIFT
        "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> Kind.FLUSSONIC
        // Bez vrste, ali sa šablonom: šablon je cela adresa.
        null, "" -> if (source.isNullOrBlank()) null else Kind.DEFAULT
        else -> null
    }

    /** Menja oznake u šablonu; prihvata i `${ime}` i `{ime}`. Vremena su u sekundama (Unix). */
    private fun fill(template: String, start: Long, stop: Long, now: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = start * 1000 }
        fun two(field: Int, add: Int = 0) = (calendar.get(field) + add).toString().padStart(2, '0')
        val values = mapOf(
            "utc" to start.toString(),
            "start" to start.toString(),
            "utcend" to stop.toString(),
            "end" to stop.toString(),
            "lutc" to now.toString(),
            "now" to now.toString(),
            "timestamp" to now.toString(),
            "duration" to (stop - start).toString(),
            "offset" to (now - start).coerceAtLeast(0).toString(),
            "Y" to calendar.get(Calendar.YEAR).toString(),
            "m" to two(Calendar.MONTH, 1),
            "d" to two(Calendar.DAY_OF_MONTH),
            "H" to two(Calendar.HOUR_OF_DAY),
            "M" to two(Calendar.MINUTE),
            "S" to two(Calendar.SECOND),
        )
        return PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
    }

    /** Flussonic: vreme se upisuje u poslednji deo putanje (`index.m3u8` → `index-<start>-<trajanje>.m3u8`). */
    private fun flussonic(streamUrl: String, start: Long, duration: Long): String? {
        val query = streamUrl.substringAfter('?', "")
        val path = streamUrl.substringBefore('?')
        val slash = path.lastIndexOf('/')
        if (slash < "http://x".length) return null
        val last = path.substring(slash + 1)
        val replaced = when {
            last.endsWith(".m3u8") -> last.removeSuffix(".m3u8") + "-$start-$duration.m3u8"
            last == "mpegts" -> "timeshift_abs-$start.ts"
            else -> return null
        }
        return path.substring(0, slash + 1) + replaced + if (query.isEmpty()) "" else "?$query"
    }

    private val PLACEHOLDER = Regex("""\$?\{([A-Za-z]+)\}""")
}
