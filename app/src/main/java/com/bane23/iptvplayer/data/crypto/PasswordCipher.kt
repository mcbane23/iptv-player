package com.bane23.iptvplayer.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Šifrovanje lozinki pre upisa u bazu. */
interface PasswordCipher {
    fun encrypt(plain: String): String

    /** Baca izuzetak ako tekst ne može da se dešifruje (npr. ključ više ne postoji). */
    fun decrypt(encrypted: String): String
}

/** AES-256-GCM sa ključem koji živi u Android Keystore-u i ne može da se izvuče sa uređaja. */
@Singleton
class KeystorePasswordCipher @Inject constructor() : PasswordCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /**
     * Već dešifrovane lozinke, po šifrovanom tekstu. Keystore je spor (na TV uređajima i desetine
     * milisekundi), a lozinka treba pri svakoj promeni kanala i svakom spisku filmova. Živi samo u
     * memoriji, dok aplikacija radi.
     */
    private val decrypted = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun decrypt(encrypted: String): String {
        decrypted[encrypted]?.let { return it }
        return decryptNow(encrypted).also { decrypted[encrypted] = it }
    }

    private fun decryptNow(encrypted: String): String {
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        require(bytes.size > IV_SIZE) { "Šifrovani tekst je prekratak" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "playlist_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
