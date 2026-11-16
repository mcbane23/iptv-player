package com.bane23.iptvplayer.data.trakt

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** PKCE (S256): nasumična tajna za jednu prijavu i njen SHA-256 otisak koji se šalje unapred. */
object Pkce {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /** 64 znaka iz skupa A-Z, a-z, 0-9, `-` i `_`. */
    fun newVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, FLAGS)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, FLAGS)
    }
}
