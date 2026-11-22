package com.bane23.iptvplayer.data.backup

import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferTraktLogin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Sve što aplikacija ume da sačuva u fajl i vrati iz njega: liste sa svime što je korisnik na njima
 * uredio ([bundle], isti oblik kao prenos sa telefona na TV), podešavanja i nalozi.
 */
@Serializable
data class Backup(
    /** Kad je izvoz napravljen (ms). */
    val createdAt: Long = 0,
    /** Vrsta uređaja na kome je napravljen: [DEVICE_PHONE] ili [DEVICE_TV]. */
    val device: String = "",
    val appVersion: String = "",
    val bundle: TransferBundle = TransferBundle(),
    /** Podešavanja po ključu iz `SettingsStore`; vrednost je uvek tekst. */
    val prefs: Map<String, String> = emptyMap(),
    /** Podešavanja vezana za uređaj (dekoder, veličina prikaza...); vraćaju se samo na istu vrstu uređaja. */
    val devicePrefs: Map<String, String> = emptyMap(),
    /** Nazivi svih imenovanih lista omiljenih, redom kojim su poređane (i praznih). */
    val favoriteLists: List<String> = emptyList(),
    /** Prijava na Trakt nalog. */
    val trakt: TransferTraktLogin? = null,
    /** Jezik aplikacije, kao naziv iz `AppLanguage`. */
    val language: String? = null,
) {
    companion object {
        const val DEVICE_PHONE = "phone"
        const val DEVICE_TV = "tv"
    }
}

/** Omot fajla: običan izvoz nosi [backup], a zaštićen lozinkom šifrovan sadržaj u [data]. */
@Serializable
data class BackupEnvelope(
    val format: String = "",
    val version: Int = 0,
    val encrypted: Boolean = false,
    val backup: Backup? = null,
    val salt: String? = null,
    val iv: String? = null,
    val data: String? = null,
)

/** Ishod čitanja fajla izvoza. */
sealed interface BackupRead {
    data class Ok(val backup: Backup) : BackupRead

    /** Fajl je zaštićen lozinkom, a lozinka nije data. */
    data object NeedsPassword : BackupRead

    data object WrongPassword : BackupRead

    /** Fajl je napravila novija verzija aplikacije. */
    data object Newer : BackupRead

    /** Ovo nije fajl izvoza ili je oštećen. */
    data object Invalid : BackupRead
}

@OptIn(ExperimentalEncodingApi::class)
object BackupCodec {
    const val FORMAT = "glowbox-backup"
    const val VERSION = 1

    /** Nastavak fajla; običan izvoz je čitljiv JSON, pa i zaštićen ostaje pod istim nastavkom. */
    const val EXTENSION = "json"

    /** Veći fajl se ne čita: toliki izvoz ne postoji, a čitanje bi potrošilo svu memoriju. */
    const val MAX_FILE_BYTES = 64L * 1024 * 1024

    private const val KEY_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val pretty = Json(json) { prettyPrint = true }

    /** Tekst fajla. Uz [password] je sadržaj šifrovan (AES-GCM, ključ izveden iz lozinke). */
    fun encode(backup: Backup, password: String? = null): String {
        if (password.isNullOrEmpty()) {
            return pretty.encodeToString(BackupEnvelope.serializer(), BackupEnvelope(FORMAT, VERSION, backup = backup))
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val plain = gzip(json.encodeToString(Backup.serializer(), backup).toByteArray())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val envelope = BackupEnvelope(
            format = FORMAT,
            version = VERSION,
            encrypted = true,
            salt = Base64.encode(salt),
            iv = Base64.encode(iv),
            data = Base64.encode(cipher.doFinal(plain)),
        )
        return json.encodeToString(BackupEnvelope.serializer(), envelope)
    }

    fun decode(text: String, password: String? = null): BackupRead {
        val envelope = try {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        } catch (_: SerializationException) {
            return BackupRead.Invalid
        } catch (_: IllegalArgumentException) {
            return BackupRead.Invalid
        }
        if (envelope.format != FORMAT || envelope.version < 1) return BackupRead.Invalid
        if (envelope.version > VERSION) return BackupRead.Newer
        if (!envelope.encrypted) return envelope.backup?.let { BackupRead.Ok(it) } ?: BackupRead.Invalid
        if (password.isNullOrEmpty()) return BackupRead.NeedsPassword
        val plain = try {
            val salt = Base64.decode(envelope.salt ?: return BackupRead.Invalid)
            val iv = Base64.decode(envelope.iv ?: return BackupRead.Invalid)
            val data = Base64.decode(envelope.data ?: return BackupRead.Invalid)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(data)
        } catch (_: IllegalArgumentException) {
            return BackupRead.Invalid
        } catch (_: GeneralSecurityException) {
            // Provera celovitosti nije prošla: lozinka nije ta (ili je fajl menjan).
            return BackupRead.WrongPassword
        }
        return try {
            BackupRead.Ok(json.decodeFromString(Backup.serializer(), String(gunzip(plain))))
        } catch (_: SerializationException) {
            BackupRead.Invalid
        } catch (_: IllegalArgumentException) {
            BackupRead.Invalid
        } catch (_: IOException) {
            BackupRead.Invalid
        }
    }

    /** Sadržaj liste iz fajla, sažet i zapisan kao tekst. */
    fun pack(bytes: ByteArray): String = Base64.encode(gzip(bytes))

    /** Obrnuto od [pack]; null ako tekst nije ispravan. */
    fun unpack(text: String): ByteArray? = try {
        gunzip(Base64.decode(text))
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        // HmacSHA1 varijanta postoji na svim verzijama Androida koje aplikacija podržava.
        val spec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw IOException("too large")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
