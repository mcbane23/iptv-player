package com.bane23.iptvplayer.data.update

import com.bane23.iptvplayer.BuildConfig

/**
 * Kanal ažuriranja: zvanična izdanja ili test buildovi. Oba su potpisana istim ključem i dele
 * isto brojanje verzija, pa se sa jednog na drugi prelazi običnim ažuriranjem, bez gubitka podataka.
 * [tag] je oznaka izdanja u javnom repou.
 */
enum class UpdateChannel(val tag: String) {
    RELEASE("iptv-player"),
    DEV("iptv-player-dev"),
    ;

    companion object {
        /** Kanal iz koga je ovaj build (zvanično izdanje ili test build); važi dok korisnik ne izabere drugi. */
        val DEFAULT: UpdateChannel = fromTag(BuildConfig.UPDATE_CHANNEL) ?: RELEASE

        fun fromTag(tag: String?): UpdateChannel? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Gde aplikacija traži nova izdanja: javni repo samo sa APK-ovima.
 * U repou svaka aplikacija ima svoja izdanja po imenu taga, pa tu mogu stajati i druge aplikacije.
 * Isti repo i tagove koriste `.github/workflows/build.yml` (dev) i `release.yml` (zvanična izdanja).
 * Tag se ne pomera, CI menja fajlove u njemu.
 */
object UpdateSource {
    const val REPO = "mcbane23/apps"

    const val MANIFEST_FILE = "iptv-player.json"

    private fun base(channel: UpdateChannel) = "https://github.com/$REPO/releases/download/${channel.tag}/"

    fun manifestUrl(channel: UpdateChannel): String = base(channel) + MANIFEST_FILE

    fun apkUrl(channel: UpdateChannel, manifest: UpdateManifest): String = base(channel) + manifest.apk
}
