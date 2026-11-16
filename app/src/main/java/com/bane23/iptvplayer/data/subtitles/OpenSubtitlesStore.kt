package com.bane23.iptvplayer.data.subtitles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bane23.iptvplayer.BuildConfig
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Da li je APK napravljen sa API ključem za OpenSubtitles (GitHub Secrets); bez njega se pretraga
 * titlova na internetu ne prikazuje.
 */
val isOpenSubtitlesConfigured: Boolean = BuildConfig.OPENSUBTITLES_API_KEY.isNotEmpty()

private val Context.openSubtitlesDataStore: DataStore<Preferences> by preferencesDataStore(name = "opensubtitles")

/** Prijava na OpenSubtitles nalog. */
class OpenSubtitlesAccount(val username: String, val password: String, val token: String?)

/**
 * Korisnikov OpenSubtitles nalog (nije obavezan). Token važi kratko, pa se uz njega čuvaju i podaci
 * za prijavu, da se prijava sama ponovi. Lozinka i token su šifrovani ključem iz Android Keystore-a.
 */
@Singleton
class OpenSubtitlesStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: PasswordCipher,
) {
    private val dataStore = context.openSubtitlesDataStore

    /** Korisničko ime prijavljenog naloga, ili null kad prijave nema. */
    val username: Flow<String?> = dataStore.data
        .map { prefs -> prefs[USERNAME].takeIf { prefs[PASSWORD] != null } }
        .distinctUntilChanged()

    suspend fun account(): OpenSubtitlesAccount? {
        val prefs = dataStore.data.first()
        val username = prefs[USERNAME] ?: return null
        val password = prefs[PASSWORD] ?: return null
        return try {
            OpenSubtitlesAccount(username, cipher.decrypt(password), prefs[TOKEN]?.let(cipher::decrypt))
        } catch (_: Exception) {
            // Ključ za šifrovanje više ne postoji (npr. vraćanje podataka na drugi uređaj): prijava se ponavlja.
            null
        }
    }

    suspend fun save(username: String, password: String, token: String) {
        dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[PASSWORD] = cipher.encrypt(password)
            prefs[TOKEN] = cipher.encrypt(token)
        }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN) }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val TOKEN = stringPreferencesKey("token")
    }
}
