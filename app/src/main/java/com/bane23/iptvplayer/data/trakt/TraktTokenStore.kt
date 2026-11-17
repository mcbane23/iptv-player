package com.bane23.iptvplayer.data.trakt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
 * Da li je APK napravljen sa Client ID-jem Trakt aplikacije (GitHub Secrets); bez njega Trakt se ne
 * prikazuje. Client Secret nije obavezan: nove Trakt aplikacije ga nemaju (prijava preko PKCE).
 */
val isTraktConfigured: Boolean = BuildConfig.TRAKT_CLIENT_ID.isNotEmpty()

private val Context.traktDataStore: DataStore<Preferences> by preferencesDataStore(name = "trakt")

/** Prijava na Trakt nalog. Tokeni se čuvaju šifrovani ključem iz Android Keystore-a. */
data class TraktSession(val accessToken: String, val refreshToken: String, val expiresAtMs: Long, val username: String?)

@Singleton
class TraktTokenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: PasswordCipher,
) {
    private val dataStore = context.traktDataStore

    val isAvailable: Boolean get() = isTraktConfigured

    /** Korisničko ime prijavljenog naloga ("" ako nije poznato), ili null kad prijave nema. */
    val username: Flow<String?> = dataStore.data
        .map { prefs -> if (prefs[ACCESS] == null) null else prefs[USERNAME].orEmpty() }
        .distinctUntilChanged()

    /** Vreme poslednje uspešne sinhronizacije, ili null. */
    val lastSyncMs: Flow<Long?> = dataStore.data.map { it[LAST_SYNC] }.distinctUntilChanged()

    suspend fun isSignedIn(): Boolean = dataStore.data.first()[ACCESS] != null

    suspend fun session(): TraktSession? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS] ?: return null
        val refresh = prefs[REFRESH] ?: return null
        return try {
            TraktSession(cipher.decrypt(access), cipher.decrypt(refresh), prefs[EXPIRES] ?: 0, prefs[USERNAME])
        } catch (_: Exception) {
            // Ključ za šifrovanje više ne postoji (npr. vraćanje podataka na drugi uređaj): prijava se ponavlja.
            null
        }
    }

    suspend fun save(token: TokenResponse) {
        dataStore.edit { prefs ->
            prefs[ACCESS] = cipher.encrypt(token.accessToken)
            prefs[REFRESH] = cipher.encrypt(token.refreshToken)
            prefs[EXPIRES] = (token.createdAt + token.expiresIn) * 1000
        }
    }

    suspend fun setUsername(name: String) {
        dataStore.edit { it[USERNAME] = name }
    }

    suspend fun setLastSync(millis: Long) {
        dataStore.edit { it[LAST_SYNC] = millis }
    }

    /** Vreme najnovije pozicije sa naloga koja je već primenjena ovde. */
    suspend fun lastPlaybackMs(): Long = dataStore.data.first()[LAST_PLAYBACK] ?: 0

    suspend fun setLastPlaybackMs(millis: Long) {
        dataStore.edit { it[LAST_PLAYBACK] = millis }
    }

    /**
     * Serije koje je sinhronizacija već dodala u omiljene, kao "lista/TMDB ID". Svaka se dodaje
     * samo jednom, da se serija koju korisnik ukloni iz omiljenih ne bi vraćala.
     */
    suspend fun autoFavorites(): Set<String> = dataStore.data.first()[AUTO_FAVORITES].orEmpty()

    suspend fun addAutoFavorites(keys: Collection<String>) {
        dataStore.edit { it[AUTO_FAVORITES] = it[AUTO_FAVORITES].orEmpty() + keys }
    }

    /** Briše prijavu. Spisak već dodatih u omiljene ostaje, da ponovna prijava ne vrati uklonjene. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            val autoFavorites = prefs[AUTO_FAVORITES]
            prefs.clear()
            if (autoFavorites != null) prefs[AUTO_FAVORITES] = autoFavorites
        }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val EXPIRES = longPreferencesKey("expires_at")
        val USERNAME = stringPreferencesKey("username")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val LAST_PLAYBACK = longPreferencesKey("last_playback")
        val AUTO_FAVORITES = stringSetPreferencesKey("auto_favorites")
    }
}
