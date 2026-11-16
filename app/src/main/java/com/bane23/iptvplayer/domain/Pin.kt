package com.bane23.iptvplayer.domain

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN roditeljske zaštite. Na uređaju se ne čuva sam PIN nego njegov otisak sa nasumičnim
 * dodatkom (`dodatak:otisak`), pa PIN ne može da se pročita iz podešavanja aplikacije.
 */
object Pin {
    const val LENGTH = 4

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /** Zapis za čuvanje. [salt] se zadaje samo u testovima. */
    fun hash(pin: String, salt: String = newSalt()): String = "$salt:${digest(salt, pin)}"

    /** Da li [pin] odgovara sačuvanom zapisu. */
    fun matches(pin: String, stored: String?): Boolean {
        val salt = stored?.substringBefore(':', "").orEmpty()
        val expected = stored?.substringAfter(':', "").orEmpty()
        if (salt.isEmpty() || expected.isEmpty() || !isValid(pin)) return false
        return MessageDigest.isEqual(digest(salt, pin).toByteArray(), expected.toByteArray())
    }

    private fun digest(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    private fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
