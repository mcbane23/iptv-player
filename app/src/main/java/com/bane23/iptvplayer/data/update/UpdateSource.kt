package com.bane23.iptvplayer.data.update

import com.bane23.iptvplayer.BuildConfig

/**
 * Gde aplikacija traži nova izdanja: javni repo samo sa APK-ovima (kod ostaje privatan).
 * U repou svaka aplikacija ima svoja izdanja po imenu taga, pa tu mogu stajati i druge aplikacije.
 * Isti repo i tagove koriste `.github/workflows/build.yml` (dev) i `release.yml` (zvanična izdanja).
 */
object UpdateSource {
    const val REPO = "mcbane23/apps"

    /**
     * Kanal zavisi od vrste builda: debug prati `iptv-player-dev` (svako spajanje u main), a zvanično
     * izdanje prati `iptv-player` (samo oznake `v*`). Tag se ne pomera, CI menja fajlove u njemu.
     * Kanali se ne mešaju jer su potpisani različitim ključevima.
     */
    val CHANNEL_TAG: String = BuildConfig.UPDATE_CHANNEL

    const val MANIFEST_FILE = "iptv-player.json"

    private val BASE = "https://github.com/$REPO/releases/download/$CHANNEL_TAG/"

    val manifestUrl: String get() = BASE + MANIFEST_FILE

    fun apkUrl(manifest: UpdateManifest): String = BASE + manifest.apk
}
