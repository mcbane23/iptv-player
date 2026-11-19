package com.bane23.iptvplayer.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Opis poslednjeg builda u javnom repou za APK-ove. CI ga objavljuje kao `iptv-player.json`
 * pored APK-a, u izdanju izabranog kanala ([UpdateChannel]).
 */
@Serializable
data class UpdateManifest(
    /** Raste sa svakim spajanjem u main, isto za oba kanala. Novije je samo ono sa većim brojem. */
    val versionCode: Long,
    val versionName: String,
    /** Ime APK fajla u istom izdanju. */
    val apk: String,
    /** SHA-256 APK-a (mala slova, hex). Proverava se posle preuzimanja ako postoji. */
    val sha256: String? = null,
    /** Kratak commit, samo za prikaz. */
    val commit: String? = null,
    /**
     * Test buildovi: naslovi poslednjih izmena spojenih u main, najnovija prva. Svaka nosi broj
     * builda u kom se pojavila, pa aplikacija prikazuje samo ono što je novije od instaliranog.
     */
    val changes: List<UpdateChange> = emptyList(),
    /** Zvanična izdanja: ručno pisan opis novosti po jeziku (`sr`, `en`), jedna stavka po redu. */
    val notes: Map<String, String> = emptyMap(),
)

@Serializable
data class UpdateChange(val code: Long, val title: String)

/** Šta je novo u ponuđenoj verziji, kao spisak kratkih stavki za prozor ažuriranja. */
object UpdateNotes {
    private const val MAX_LINES = 40
    private const val MAX_LINE_LENGTH = 300
    private val bullet = Regex("^[-*•]\\s+")

    /**
     * Ručno pisan opis ima prednost (zvanična izdanja); bez njega se nabrajaju izmene novije od
     * instaliranog builda. [language] je jezik aplikacije: srpski čita `sr`, svi ostali `en`,
     * a ako tog jezika nema, uzima se onaj koji postoji.
     */
    fun lines(manifest: UpdateManifest, installedVersionCode: Long, language: String): List<String> {
        val preferred = if (language == "sr") "sr" else "en"
        val text = manifest.notes[preferred]?.takeIf { it.isNotBlank() }
            ?: manifest.notes.values.firstOrNull { it.isNotBlank() }
        val raw = text?.lines()
            ?: manifest.changes
                .filter { it.code > installedVersionCode }
                .sortedByDescending { it.code }
                .map { it.title }
        return raw.asSequence()
            .map { it.trim().replace(bullet, "").take(MAX_LINE_LENGTH) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_LINES)
            .toList()
    }
}

object UpdateManifestParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val apkName = Regex("^[A-Za-z0-9._-]+\\.apk$")
    private val sha256Hex = Regex("^[0-9a-f]{64}$")

    /** Čita manifest; vraća null ako je neispravan ili sumnjiv (npr. ime APK-a sa putanjom). */
    fun parse(text: String): UpdateManifest? {
        val manifest = runCatching { json.decodeFromString(UpdateManifest.serializer(), text) }
            .getOrNull() ?: return null
        if (manifest.versionCode <= 0) return null
        if (!apkName.matches(manifest.apk)) return null
        val sha = manifest.sha256?.lowercase()
        if (sha != null && !sha256Hex.matches(sha)) return null
        return manifest.copy(sha256 = sha)
    }

    /** Ažuriranje postoji samo kad je build noviji od instaliranog (nikad vraćanje na stariji). */
    fun isNewer(manifest: UpdateManifest, installedVersionCode: Long): Boolean =
        manifest.versionCode > installedVersionCode
}
