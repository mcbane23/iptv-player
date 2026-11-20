package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.domain.Pin
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Roditeljska zaštita: PIN i zaključane kategorije. Tačan PIN otključava sve do gašenja
 * aplikacije (ili dok se u podešavanjima ponovo ne zaključa).
 */
@Singleton
class LockRepository @Inject constructor(
    private val settings: SettingsStore,
    private val db: AppDatabase,
) {
    private val _unlocked = MutableStateFlow(false)

    /** PIN je unet u ovom pokretanju aplikacije. */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val hasPin: Flow<Boolean> = settings.pinRecord.map { it != null }

    /** Ključevi zaključanih kategorija, bez obzira na to da li je trenutno otključano. */
    val lockedKeys: Flow<Set<String>> = settings.lockedCategories

    /** Ključevi kategorija čiji je sadržaj sada nedostupan (prazno kad nema PIN-a ili je otključano). */
    val activeKeys: Flow<Set<String>> = combine(settings.pinRecord, settings.lockedCategories, _unlocked) { pin, keys, open ->
        if (pin == null || open) emptySet() else keys
    }

    suspend fun activeKeysNow(): Set<String> = activeKeys.first()

    /** Proverava PIN; tačan otključava zaključane kategorije. */
    suspend fun unlock(pin: String): Boolean {
        val ok = Pin.matches(pin, settings.pinRecord.first())
        if (ok) _unlocked.value = true
        return ok
    }

    fun lockAgain() {
        _unlocked.value = false
    }

    /** Postavlja novi PIN. Vraća false ako nije od četiri cifre. */
    suspend fun setPin(pin: String): Boolean {
        if (!Pin.isValid(pin)) return false
        settings.setPinRecord(Pin.hash(pin))
        _unlocked.value = true
        return true
    }

    /** Uklanja PIN i sva zaključavanja. */
    suspend fun removePin() {
        settings.setPinRecord(null)
        _unlocked.value = false
    }

    suspend fun setLocked(key: String, locked: Boolean) = settings.setCategoryLocked(key, locked)

    /** Da li kanal sada sme da se pusti tek posle PIN-a. */
    suspend fun isChannelLocked(channelId: Long): Boolean {
        val keys = activeKeysNow()
        if (keys.isEmpty()) return false
        val channel = db.channelDao().getById(channelId) ?: return false
        val categoryId = channel.categoryId ?: return false
        val category = db.categoryDao().getByPlaylist(channel.playlistId).firstOrNull { it.id == categoryId }
        return category != null && liveKey(channel.playlistId, category.name) in keys
    }

    /** Snimak kanala iz kategorije koja je sada zaključana pušta se tek posle PIN-a. */
    suspend fun isRecordingLocked(recordingId: Long): Boolean {
        if (activeKeysNow().isEmpty()) return false
        val recording = db.recordingDao().getById(recordingId) ?: return false
        // Kanal se traži po stalnom ključu: ID se menja pri svakom osvežavanju liste.
        val channel = db.channelDao().byStableKey(recording.playlistId, recording.channelKey) ?: return false
        return isChannelLocked(channel.id)
    }

    companion object {
        /** Oznaka ispred naziva kategorije koja je sada zaključana. */
        const val LOCK_MARK = "\uD83D\uDD12 "

        /** Kategorija kanala se pamti po nazivu, jer se njen ID menja kad se lista osveži. */
        fun liveKey(playlistId: Long, categoryName: String): String = "live|$playlistId|$categoryName"

        /** Kategorija filmova ([series] = false) ili serija; ID daje Xtream server i stalan je. */
        fun vodKey(playlistId: Long, series: Boolean, categoryId: String): String =
            "${if (series) "series" else "movies"}|$playlistId|$categoryId"
    }
}
