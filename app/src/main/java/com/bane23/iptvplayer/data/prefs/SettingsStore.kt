package com.bane23.iptvplayer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.PlayerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Podešavanja aplikacije (DataStore). */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.settingsDataStore

    /** Lista koju je korisnik izabrao kao aktivnu, ili null ako nijedna nije izabrana. */
    val activePlaylistId: Flow<Long?> =
        dataStore.data.map { it[ACTIVE_PLAYLIST_ID] }.distinctUntilChanged()

    suspend fun setActivePlaylistId(id: Long?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_PLAYLIST_ID) else prefs[ACTIVE_PLAYLIST_ID] = id
        }
    }

    /** Da li se pri startu aplikacije odmah pušta poslednji gledani kanal. */
    val resumeLastChannel: Flow<Boolean> =
        dataStore.data.map { it[RESUME_LAST_CHANNEL] ?: false }.distinctUntilChanged()

    suspend fun setResumeLastChannel(enabled: Boolean) {
        dataStore.edit { it[RESUME_LAST_CHANNEL] = enabled }
    }

    /** Da li Uživo na TV-u pušta mali pregled (bez zvuka) kanala na kome je fokus. */
    val tvChannelPreview: Flow<Boolean> =
        dataStore.data.map { it[TV_CHANNEL_PREVIEW] ?: true }.distinctUntilChanged()

    suspend fun setTvChannelPreview(enabled: Boolean) {
        dataStore.edit { it[TV_CHANNEL_PREVIEW] = enabled }
    }

    /** Podešavanja plejera. Čitaju se pri otvaranju plejera, pa izmena važi od sledećeg puštanja. */
    val playerSettings: Flow<PlayerSettings> = dataStore.data.map { prefs ->
        PlayerSettings(
            buffer = BufferSize.entries.firstOrNull { it.name == prefs[PLAYER_BUFFER] } ?: BufferSize.NORMAL,
            decoder = DecoderMode.entries.firstOrNull { it.name == prefs[PLAYER_DECODER] } ?: DecoderMode.AUTO,
            audioLanguage = prefs[PLAYER_AUDIO_LANGUAGE].orEmpty(),
            subtitleLanguage = prefs[PLAYER_SUBTITLE_LANGUAGE].orEmpty(),
        )
    }.distinctUntilChanged()

    suspend fun setPlayerBuffer(buffer: BufferSize) {
        dataStore.edit { it[PLAYER_BUFFER] = buffer.name }
    }

    suspend fun setPlayerDecoder(decoder: DecoderMode) {
        dataStore.edit { it[PLAYER_DECODER] = decoder.name }
    }

    suspend fun setAudioLanguage(language: String) {
        dataStore.edit { it[PLAYER_AUDIO_LANGUAGE] = language }
    }

    suspend fun setSubtitleLanguage(language: String) {
        dataStore.edit { it[PLAYER_SUBTITLE_LANGUAGE] = language }
    }

    private companion object {
        val PLAYER_BUFFER = stringPreferencesKey("player_buffer")
        val PLAYER_DECODER = stringPreferencesKey("player_decoder")
        val PLAYER_AUDIO_LANGUAGE = stringPreferencesKey("player_audio_language")
        val PLAYER_SUBTITLE_LANGUAGE = stringPreferencesKey("player_subtitle_language")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val TV_CHANNEL_PREVIEW = booleanPreferencesKey("tv_channel_preview")
        val ACTIVE_PLAYLIST_ID = longPreferencesKey("active_playlist_id")
    }
}
