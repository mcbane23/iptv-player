package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.domain.Pin
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Roditeljska zaštita: PIN i zaključane kategorije. Tačan PIN otključava sve dok se ne izađe iz
 * aplikacije (vidi `MainActivity.onStop`) ili dok se u podešavanjima ponovo ne zaključa.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Kolona `locked` prati podešavanja: menja se kad se unese PIN, kad se ponovo zaključa, kad se
        // promeni izbor kategorija i kad osvežavanje liste upiše kategorije iznova.
        scope.launch {
            combine(activeKeys, db.categoryDao().observeAllLive()) { keys, categories ->
                categories.filter { (liveKey(it.playlistId, it.name) in keys) != it.locked }
            }.distinctUntilChanged().collect { wrong ->
                try {
                    val (toUnlock, toLock) = wrong.partition { it.locked }
                    toLock.map { it.id }.chunked(ID_CHUNK).forEach { db.categoryDao().setLocked(it, true) }
                    toUnlock.map { it.id }.chunked(ID_CHUNK).forEach { db.categoryDao().setLocked(it, false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sledeća promena u bazi ili podešavanjima pokušava ponovo.
                }
            }
        }
        // Kanali zaključanih kategorija se ne pamte među nedavno gledanim, ni dok je otključano: što je
        // već zapamćeno briše se čim se kategorija zaključa (i posle osvežavanja liste).
        scope.launch {
            combine(settings.lockedCategories, db.categoryDao().observeAllLive()) { keys, categories ->
                categories.filter { liveKey(it.playlistId, it.name) in keys }.map { it.id }.toSet()
            }.distinctUntilChanged().collect { ids ->
                try {
                    ids.chunked(ID_CHUNK).forEach { db.historyDao().deleteOfCategories(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sledeća promena u bazi ili podešavanjima pokušava ponovo.
                }
            }
        }
    }

    /**
     * Da li je strim u kategoriji koja je izabrana za zaključavanje, bez obzira na to da li je PIN sada
     * unet. Takav kanal ne ulazi u nedavno gledane.
     */
    suspend fun isInLockedCategory(playlistId: Long, stableKey: String): Boolean {
        val keys = settings.lockedCategories.first()
        if (keys.isEmpty()) return false
        return db.channelDao().categoryNamesOf(playlistId, stableKey).any { liveKey(playlistId, it) in keys }
    }

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

    /** Ima li ičeg što je sada zaključano (PIN postoji, nije unet, bar jedna kategorija je zaključana). */
    suspend fun anythingLocked(): Boolean = activeKeysNow().isNotEmpty()

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
        /** SQLite prima ograničen broj vrednosti u jednom upitu. */
        private const val ID_CHUNK = 500

        /** Oznaka ispred naziva kategorije koja je sada zaključana. */
        const val LOCK_MARK = "\uD83D\uDD12 "

        /** Kategorija kanala se pamti po nazivu, jer se njen ID menja kad se lista osveži. */
        fun liveKey(playlistId: Long, categoryName: String): String = "live|$playlistId|$categoryName"

        /** Kategorija filmova ([series] = false) ili serija; ID daje Xtream server i stalan je. */
        fun vodKey(playlistId: Long, series: Boolean, categoryId: String): String =
            "${if (series) "series" else "movies"}|$playlistId|$categoryId"
    }
}
