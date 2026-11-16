package com.bane23.iptvplayer.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Opis poslednjeg builda u javnom repou za APK-ove. CI ga objavljuje kao `iptv-player.json`
 * pored APK-a, u izdanju [UpdateSource.CHANNEL_TAG].
 */
@Serializable
data class UpdateManifest(
    /** Raste sa svakim buildom (broj CI run-a). Novije je samo ono sa većim brojem. */
    val versionCode: Long,
    val versionName: String,
    /** Ime APK fajla u istom izdanju. */
    val apk: String,
    /** SHA-256 APK-a (mala slova, hex). Proverava se posle preuzimanja ako postoji. */
    val sha256: String? = null,
    /** Kratak commit, samo za prikaz. */
    val commit: String? = null,
)

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
