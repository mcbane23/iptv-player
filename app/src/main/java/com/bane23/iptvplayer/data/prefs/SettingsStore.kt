package com.bane23.iptvplayer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.PlayerSettings
import com.bane23.iptvplayer.player.SubtitlePosition
import com.bane23.iptvplayer.player.SubtitleSize
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

    /** Da li se aplikacija sama otvara kad se uređaj upali (za TV box). */
    val autostart: Flow<Boolean> = dataStore.data.map { it[AUTOSTART] ?: false }.distinctUntilChanged()

    suspend fun setAutostart(enabled: Boolean) {
        dataStore.edit { it[AUTOSTART] = enabled }
    }

    /** Zapis PIN-a roditeljske zaštite (vidi `Pin.hash`), ili null ako PIN nije postavljen. */
    val pinRecord: Flow<String?> = dataStore.data.map { it[PIN_RECORD] }.distinctUntilChanged()

    /** Čuva novi zapis PIN-a; null uklanja PIN i sva zaključavanja. */
    suspend fun setPinRecord(record: String?) {
        dataStore.edit { prefs ->
            if (record == null) {
                prefs.remove(PIN_RECORD)
                prefs.remove(LOCKED_CATEGORIES)
            } else {
                prefs[PIN_RECORD] = record
            }
        }
    }

    /** Ključevi zaključanih kategorija (vidi `CategoryLocks`). */
    val lockedCategories: Flow<Set<String>> =
        dataStore.data.map { it[LOCKED_CATEGORIES] ?: emptySet() }.distinctUntilChanged()

    suspend fun setCategoryLocked(key: String, locked: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[LOCKED_CATEGORIES] ?: emptySet()
            prefs[LOCKED_CATEGORIES] = if (locked) current + key else current - key
        }
    }

    /** Uputstvo za gestove u plejeru na telefonu je već prikazano (prikazuje se samo prvi put). */
    val gestureHintShown: Flow<Boolean> =
        dataStore.data.map { it[GESTURE_HINT_SHOWN] ?: false }.distinctUntilChanged()

    suspend fun setGestureHintShown() {
        dataStore.edit { it[GESTURE_HINT_SHOWN] = true }
    }

    /** Podešavanja plejera. Čitaju se pri otvaranju plejera, pa izmena važi od sledećeg puštanja. */
    val playerSettings: Flow<PlayerSettings> = dataStore.data.map { prefs ->
        PlayerSettings(
            buffer = BufferSize.entries.firstOrNull { it.name == prefs[PLAYER_BUFFER] } ?: BufferSize.NORMAL,
            decoder = DecoderMode.entries.firstOrNull { it.name == prefs[PLAYER_DECODER] } ?: DecoderMode.AUTO,
            audioLanguage = prefs[PLAYER_AUDIO_LANGUAGE].orEmpty(),
            subtitleLanguage = prefs[PLAYER_SUBTITLE_LANGUAGE].orEmpty(),
            seekStepSeconds = prefs[PLAYER_SEEK_STEP]?.takeIf { it in PlayerSettings.SEEK_STEP_OPTIONS }
                ?: PlayerSettings.DEFAULT_SEEK_STEP_SECONDS,
            subtitleBackground = prefs[SUBTITLE_BACKGROUND] ?: true,
            subtitleSize = SubtitleSize.entries.firstOrNull { it.name == prefs[SUBTITLE_SIZE] } ?: SubtitleSize.NORMAL,
            subtitlePosition = SubtitlePosition.entries.firstOrNull { it.name == prefs[SUBTITLE_POSITION] }
                ?: SubtitlePosition.LOW,
        )
    }.distinctUntilChanged()

    suspend fun setPlayerBuffer(buffer: BufferSize) {
        dataStore.edit { it[PLAYER_BUFFER] = buffer.name }
    }

    suspend fun setPlayerDecoder(decoder: DecoderMode) {
        dataStore.edit { it[PLAYER_DECODER] = decoder.name }
    }

    suspend fun setSeekStep(seconds: Int) {
        dataStore.edit { it[PLAYER_SEEK_STEP] = seconds }
    }

    suspend fun setSubtitleBackground(enabled: Boolean) {
        dataStore.edit { it[SUBTITLE_BACKGROUND] = enabled }
    }

    suspend fun setSubtitleSize(size: SubtitleSize) {
        dataStore.edit { it[SUBTITLE_SIZE] = size.name }
    }

    suspend fun setSubtitlePosition(position: SubtitlePosition) {
        dataStore.edit { it[SUBTITLE_POSITION] = position.name }
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
        val PLAYER_SEEK_STEP = intPreferencesKey("player_seek_step")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val SUBTITLE_POSITION = stringPreferencesKey("subtitle_position")
        val PLAYER_AUDIO_LANGUAGE = stringPreferencesKey("player_audio_language")
        val PLAYER_SUBTITLE_LANGUAGE = stringPreferencesKey("player_subtitle_language")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val TV_CHANNEL_PREVIEW = booleanPreferencesKey("tv_channel_preview")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val PIN_RECORD = stringPreferencesKey("pin_record")
        val LOCKED_CATEGORIES = stringSetPreferencesKey("locked_categories")
        val GESTURE_HINT_SHOWN = booleanPreferencesKey("gesture_hint_shown")
        val ACTIVE_PLAYLIST_ID = longPreferencesKey("active_playlist_id")
    }
}
