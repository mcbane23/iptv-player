package com.bane23.iptvplayer.data.update

/**
 * Gde aplikacija traži nova izdanja: javni repo samo sa APK-ovima (kod ostaje privatan).
 * U repou svaka aplikacija ima svoja izdanja po imenu taga, pa tu mogu stajati i druge aplikacije.
 * Isti repo i tag koristi `.github/workflows/build.yml`.
 */
object UpdateSource {
    const val REPO = "mcbane23/apps"

    /** Dev kanal: tag se ne pomera, CI menja fajlove u njemu na svaki build. Posle v1.0 prelazi na izdanja. */
    const val CHANNEL_TAG = "iptv-player-dev"

    const val MANIFEST_FILE = "iptv-player.json"

    private const val BASE = "https://github.com/$REPO/releases/download/$CHANNEL_TAG/"

    val manifestUrl: String get() = BASE + MANIFEST_FILE

    fun apkUrl(manifest: UpdateManifest): String = BASE + manifest.apk
}
