package com.bane23.iptvplayer.data.cast

import java.security.SecureRandom

/**
 * Prvo povezivanje telefona: TV prikaže kod od četiri cifre, a telefon ga pošalje nazad. Kod važi
 * kratko i samo za nekoliko pokušaja, da drugi uređaj u mreži ne može da ga pogodi.
 */
class CastPairing(
    private val now: () -> Long = System::currentTimeMillis,
    private val newCode: () -> String = ::randomCode,
) {
    private var code: String? = null
    private var expiresAt = 0L
    private var attemptsLeft = 0

    /** Kod koji trenutno važi, ili null. */
    @Synchronized
    fun current(): String? = code?.takeIf { now() < expiresAt }

    @Synchronized
    fun begin(): String {
        val fresh = newCode()
        code = fresh
        expiresAt = now() + VALID_MS
        attemptsLeft = MAX_ATTEMPTS
        return fresh
    }

    /** Tačan kod se troši; posle previše pogrešnih pokušaja kod prestaje da važi. */
    @Synchronized
    fun confirm(input: String): Boolean {
        val expected = code?.takeIf { now() < expiresAt }
        if (expected == null) {
            code = null
            return false
        }
        if (input == expected) {
            code = null
            return true
        }
        attemptsLeft--
        if (attemptsLeft <= 0) code = null
        return false
    }

    @Synchronized
    fun cancel() {
        code = null
    }

    companion object {
        const val CODE_LENGTH = 4
        const val VALID_MS = 120_000L
        const val MAX_ATTEMPTS = 5

        fun randomCode(): String {
            val random = SecureRandom()
            return (1..CODE_LENGTH).map { random.nextInt(10) }.joinToString("")
        }
    }
}
