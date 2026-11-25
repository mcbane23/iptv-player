package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.FavoriteEntity
import com.bane23.iptvplayer.data.db.FavoriteListEntity
import com.bane23.iptvplayer.data.db.FavoriteListItemEntity
import com.bane23.iptvplayer.data.backup.BackupCodec
import com.bane23.iptvplayer.data.db.HiddenChannelEntity
import com.bane23.iptvplayer.data.db.HistoryEntity
import com.bane23.iptvplayer.data.db.WatchProgressEntity
import com.bane23.iptvplayer.data.lan.TransferHistory
import com.bane23.iptvplayer.data.lan.TransferLockedItem
import com.bane23.iptvplayer.data.lan.TransferProgress
import com.bane23.iptvplayer.data.lan.TransferReminder
import com.bane23.iptvplayer.data.reminder.Reminder
import com.bane23.iptvplayer.data.reminder.ReminderRepository
import com.bane23.iptvplayer.data.db.PlaylistEntity
import com.bane23.iptvplayer.data.db.VodFavoriteEntity
import com.bane23.iptvplayer.data.lan.TransferAccounts
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCategory
import com.bane23.iptvplayer.data.lan.TransferFavoriteList
import com.bane23.iptvplayer.data.lan.TransferHiddenChannel
import com.bane23.iptvplayer.data.lan.TransferLocks
import com.bane23.iptvplayer.data.lan.TransferLogin
import com.bane23.iptvplayer.data.lan.TransferPlaylist
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.lan.TransferSettings
import com.bane23.iptvplayer.data.lan.TransferTraktLogin
import com.bane23.iptvplayer.data.lan.TransferVodCategories
import com.bane23.iptvplayer.data.lan.TransferVodFavorite
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.prefs.VodCategoryPrefs
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesRepository
import com.bane23.iptvplayer.data.trakt.TokenResponse
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.domain.PlaylistDraft
import com.bane23.iptvplayer.domain.PlaylistException
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.LoudnessLevel
import com.bane23.iptvplayer.player.PlayerSettings
import com.bane23.iptvplayer.player.SubtitlePosition
import com.bane23.iptvplayer.player.SubtitleSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Paket spreman za slanje i broj lista koje nisu mogle da uđu u njega. */
data class TransferExport(val bundle: TransferBundle, val skipped: Int)

/**
 * Prenos svih lista i podešavanja sa jednog uređaja na drugi: [exportBundle] pravi paket na telefonu,
 * [importBundle] ga primenjuje na TV-u. Ništa se ne briše; liste koje TV već ima se ne dodaju ponovo.
 */
@Singleton
class TransferRepository @Inject constructor(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val settings: SettingsStore,
    private val cipher: PasswordCipher,
    private val openSubtitles: OpenSubtitlesRepository,
    private val trakt: TraktRepository,
    private val locks: LockRepository,
    private val reminders: ReminderRepository,
) {
    /**
     * Uz [full] (izvoz u fajl) paket nosi i liste iz fajla sa sadržajem, pojedinačna zaključavanja,
     * nedavno gledano, pozicije filmova i epizoda i podsetnike.
     */
    suspend fun exportBundle(full: Boolean = false): TransferExport = withContext(Dispatchers.IO) {
        var skipped = 0
        val activeId = settings.activePlaylistId.first()
        val allReminders = if (full) reminders.reminders.first() else emptyList()
        val items = db.playlistDao().getAll().sortedBy { it.id }.mapNotNull { entity ->
            val item = entity.toTransfer(full)?.copy(active = entity.id == activeId)?.let { plain ->
                if (full) plain.withExtras(entity.id, allReminders) else plain
            }
            if (item == null) skipped++
            item
        }
        val player = settings.playerSettings.first()
        val bundle = TransferBundle(
            playlists = items,
            settings = TransferSettings(
                resumeLastChannel = settings.resumeLastChannel.first(),
                buffer = player.buffer.name,
                audioLanguage = player.audioLanguages.firstOrNull().orEmpty(),
                subtitleLanguage = player.subtitleLanguages.firstOrNull().orEmpty(),
                audioLanguages = player.audioLanguageSlots,
                subtitleLanguages = player.subtitleLanguageSlots,
                seekStepSeconds = player.seekStepSeconds,
                subtitleBackground = player.subtitleBackground,
                subtitleSize = player.subtitleSize.name,
                subtitlePosition = player.subtitlePosition.name,
                loudness = player.loudness.name,
                autoRefreshPlaylists = settings.autoRefreshPlaylists.first(),
                accentColor = settings.accentColor.first(),
                vodSortMovies = settings.vodSort(series = false).first(),
                vodSortSeries = settings.vodSort(series = true).first(),
            ),
            pinRecord = settings.pinRecord.first(),
            accounts = openSubtitles.credentials()?.let { (username, password) ->
                TransferAccounts(openSubtitles = TransferLogin(username, password))
            },
        )
        TransferExport(bundle, skipped)
    }

    /** Ono što ide samo u izvoz u fajl. */
    private suspend fun TransferPlaylist.withExtras(playlistId: Long, allReminders: List<Reminder>) = copy(
        lockedItems = db.lockedItemDao().byPlaylist(playlistId).map { TransferLockedItem(it.kind, it.itemKey, it.name) },
        history = db.historyDao().byPlaylist(playlistId).map { TransferHistory(it.channelKey, it.watchedAt) },
        progress = db.watchProgressDao().byPlaylist(playlistId).map {
            TransferProgress(
                kind = it.kind, itemId = it.itemId, seriesId = it.seriesId, positionMs = it.positionMs,
                durationMs = it.durationMs, updatedAt = it.updatedAt, title = it.title, poster = it.poster,
                extension = it.extension, watchedAt = it.watchedAt, dismissedAt = it.dismissedAt,
            )
        },
        reminders = allReminders.filter { it.playlistId == playlistId }
            .map { TransferReminder(it.channelKey, it.startMs, it.channelName, it.title) },
    )

    /**
     * Lista iz fajla ide samo u izvoz u fajl ([full]), zajedno sa sadržajem; pri prenosu na TV ostaje
     * na telefonu. Ne ide ni lista čija lozinka ne može da se pročita.
     */
    private suspend fun PlaylistEntity.toTransfer(full: Boolean): TransferPlaylist? {
        var fileContent: String? = null
        val type = when (type) {
            PlaylistType.XTREAM -> TransferPlaylist.TYPE_XTREAM
            PlaylistType.M3U_URL -> TransferPlaylist.TYPE_M3U
            PlaylistType.M3U_FILE -> {
                if (!full) return null
                fileContent = BackupCodec.pack(playlists.readFileContent(id, MAX_FILE_LIST_BYTES) ?: return null)
                TransferPlaylist.TYPE_FILE
            }
        }
        val password = if (this.type == PlaylistType.XTREAM) {
            try {
                cipher.decrypt(passwordEncrypted ?: return null)
            } catch (_: Exception) {
                return null
            }
        } else {
            ""
        }
        val categories = db.categoryDao().getByPlaylist(id)
            .filter { it.hidden || it.userOrder != null }
            .map { TransferCategory(name = it.name, type = it.type.name, hidden = it.hidden, order = it.userOrder) }
        return TransferPlaylist(
            type = type,
            name = name,
            fileContent = fileContent,
            // Adresa fajla važi samo na ovom uređaju.
            source = if (type == TransferPlaylist.TYPE_FILE) "" else source,
            username = username.orEmpty(),
            password = password,
            userAgent = userAgent.orEmpty(),
            epgUrl = epgUrlOverride.orEmpty(),
            epgShiftHours = epgShiftHours,
            favorites = db.favoriteDao().keysByPlaylist(id),
            favoriteLists = db.favoriteListDao().namedItems(id).groupBy({ it.listName }, { it.channelKey })
                .map { (listName, keys) -> TransferFavoriteList(listName, keys) },
            merged = merged,
            hiddenChannels = db.hiddenChannelDao().byPlaylist(id).map { TransferHiddenChannel(it.channelKey, it.name) },
            categories = categories,
            vodFavorites = db.vodFavoriteDao().getByPlaylist(id).map {
                TransferVodFavorite(kind = it.kind, itemId = it.itemId, name = it.name, poster = it.poster, extension = it.extension)
            },
            locked = settings.lockedCategoriesOf(id).let {
                TransferLocks(live = it["live"].orEmpty(), movies = it["movies"].orEmpty(), series = it["series"].orEmpty())
            },
            vodCategories = settings.vodCategoryPrefs(id).let {
                TransferVodCategories(it.hiddenMovies, it.hiddenSeries, it.orderMovies, it.orderSeries)
            },
        )
    }

    /** [onProgress] javlja koja se lista upravo učitava (redni broj od 1, ukupno). */
    suspend fun importBundle(bundle: TransferBundle, onProgress: (Int, Int) -> Unit = { _, _ -> }): TransferResult {
        val pending = beginImport(bundle)
        return pending.finish(onProgress)
    }

    /**
     * Brzi deo prijema: sve što ne traži preuzimanje sa interneta (podešavanja, nalozi, liste koje TV
     * već ima) primenjuje se odmah. Nove liste se samo stavljaju u red; preuzima ih
     * [PendingImport.finish], što ume da traje minutima, pa pošiljalac na to ne čeka.
     *
     * Uz [replace] (uvoz iz fajla sa zamenom) se prvo briše sve čega u paketu nema.
     */
    suspend fun beginImport(
        bundle: TransferBundle,
        replace: Boolean = false,
        favoriteListNames: List<String> = emptyList(),
    ): PendingImport = withContext(Dispatchers.IO) {
        if (replace) removeUnlisted(bundle)
        // Imenovane liste omiljenih redom iz izvoza, i one u kojima nema nijednog kanala.
        importFavoriteLists(0, favoriteListNames.map { TransferFavoriteList(it) }, System.currentTimeMillis())
        var existing = 0
        var failed = 0
        val queued = ArrayList<Pair<PlaylistType, TransferPlaylist>>()
        for (item in bundle.playlists) {
            val type = item.playlistType()
            if (type == null) {
                failed++
                continue
            }
            val match = db.playlistDao().getAll().firstOrNull { it.matches(type, item) }
            // Ista lista dvaput u paketu se računa jednom.
            val repeated = queued.any { (queuedType, other) -> queuedType == type && other.sameSource(item) }
            when {
                match != null -> {
                    existing++
                    if (replace) {
                        clearUserData(match.id)
                        if (item.active) settings.setActivePlaylistId(match.id)
                    }
                    applyItem(match.id, item, existed = true)
                }

                repeated -> existing++

                else -> queued += type to item
            }
        }
        bundle.settings?.let { applySettings(it) }
        // PIN koji TV već ima se ne menja.
        val pin = bundle.pinRecord
        if (pin != null && PIN_RECORD.matches(pin) && settings.pinRecord.first() == null) settings.setPinRecord(pin)
        val accounts = bundle.accounts
        val base = TransferResult(
            existing = existing,
            failed = failed,
            openSubtitles = accounts?.openSubtitles?.let { adoptOpenSubtitles(it) },
            trakt = accounts?.trakt?.let { adoptTrakt(it) },
        )
        PendingImport(queued.size, base) { onProgress -> loadQueued(queued, base, onProgress) }
    }

    /** Preuzima nove liste jednu po jednu i na svaku primenjuje omiljene, kategorije i ostalo iz paketa. */
    private suspend fun loadQueued(
        queued: List<Pair<PlaylistType, TransferPlaylist>>,
        base: TransferResult,
        onProgress: (Int, Int) -> Unit,
    ): TransferResult = withContext(Dispatchers.IO) {
        var added = 0
        var failed = base.failed
        queued.forEachIndexed { index, (type, item) ->
            onProgress(index + 1, queued.size)
            val draft = item.toDraft(type)
            if (draft == null) {
                failed++
                return@forEachIndexed
            }
            val playlistId = try {
                playlists.addPlaylist(draft)
            } catch (_: PlaylistException) {
                if (type == PlaylistType.M3U_FILE) playlists.discardImportedFile(draft.source)
                failed++
                return@forEachIndexed
            }
            added++
            applyItem(playlistId, item, existed = false)
            if (item.active) settings.setActivePlaylistId(playlistId)
        }
        base.copy(added = added, failed = failed)
    }

    private fun TransferPlaylist.playlistType(): PlaylistType? = when (type) {
        TransferPlaylist.TYPE_XTREAM -> PlaylistType.XTREAM
        TransferPlaylist.TYPE_M3U -> PlaylistType.M3U_URL
        TransferPlaylist.TYPE_FILE -> PlaylistType.M3U_FILE.takeIf { fileContent != null }
        else -> null
    }

    /** Zamena pri uvozu: briše liste kojih u paketu nema i sve imenovane liste omiljenih. */
    private suspend fun removeUnlisted(bundle: TransferBundle) {
        val incoming = bundle.playlists.mapNotNull { item -> item.playlistType()?.let { it to item } }
        for (entity in db.playlistDao().getAll()) {
            if (incoming.none { (type, item) -> entity.matches(type, item) }) playlists.deletePlaylist(entity.id)
        }
        db.favoriteListDao().deleteAll()
    }

    /**
     * Zamena pri uvozu: sa liste koja ostaje briše sve što je korisnik na njoj uredio, da posle
     * važi samo ono iz fajla. Zaključavanja ostaju dok na uređaju postoji PIN, da uvoz ne može da
     * otključa ništa.
     */
    private suspend fun clearUserData(playlistId: Long) {
        db.favoriteDao().deleteByPlaylist(playlistId)
        db.favoriteListDao().deleteItemsByPlaylist(playlistId)
        db.hiddenChannelDao().deleteByPlaylist(playlistId)
        db.vodFavoriteDao().deleteByPlaylist(playlistId)
        db.historyDao().clear(playlistId)
        db.watchProgressDao().deleteByPlaylist(playlistId)
        for (reminder in reminders.reminders.first()) {
            if (reminder.playlistId == playlistId) reminders.remove(reminder)
        }
        if (settings.pinRecord.first() == null) {
            settings.clearPlaylistLocks(playlistId)
            for (item in db.lockedItemDao().byPlaylist(playlistId)) db.lockedItemDao().delete(playlistId, item.kind, item.itemKey)
        }
    }

    /** Omiljeni, liste omiljenih, sakriveni kanali, kategorije i zaključavanja jedne liste iz paketa. */
    private suspend fun applyItem(playlistId: Long, item: TransferPlaylist, existed: Boolean) {
            val now = System.currentTimeMillis()
            item.favorites.take(MAX_FAVORITES).forEachIndexed { position, key ->
                // Redosled dodavanja se čuva kroz vreme upisa.
                db.favoriteDao().insert(FavoriteEntity(playlistId, key, now + position))
            }
            importFavoriteLists(playlistId, item.favoriteLists, now)
            // Sakriveni kanali se samo dodaju; ono što je na TV-u već sakriveno ostaje sakriveno.
            item.hiddenChannels.asSequence()
                .filter { it.key.isNotEmpty() && it.key.length <= MAX_TEXT && it.name.length <= MAX_TEXT }
                .take(MAX_FAVORITES)
                .forEachIndexed { position, hidden ->
                    db.hiddenChannelDao().insert(HiddenChannelEntity(playlistId, hidden.key, hidden.name, now + position))
                }
            // Telefon je glavni: spajanje lista je na TV-u isto kao na telefonu, i važi za sve liste.
            item.merged?.let { db.playlistDao().setAllMerged(it) }
            db.vodFavoriteDao().insertMissing(
                item.vodFavorites.asSequence()
                    .filter { it.kind in VOD_KINDS && it.name.length <= MAX_TEXT && (it.poster?.length ?: 0) <= MAX_TEXT }
                    .take(MAX_FAVORITES)
                    .mapIndexed { position, favorite ->
                        VodFavoriteEntity(
                            playlistId = playlistId,
                            kind = favorite.kind,
                            itemId = favorite.itemId,
                            name = favorite.name,
                            poster = favorite.poster,
                            extension = favorite.extension?.take(MAX_LANGUAGE_LENGTH),
                            addedAt = now + position,
                        )
                    }
                    .toList(),
            )
            // Telefon je glavni: i na listi koju TV već ima važi uređenje kategorija sa telefona.
            if (existed) db.categoryDao().resetUserChanges(playlistId)
            applyCategories(playlistId, item.categories)
            item.vodCategories?.let { settings.replaceVodCategoryPrefs(playlistId, it.toPrefs()) }
            applyExtras(playlistId, item)
            // Zaključavanja se samo dodaju, da prenos ne može da otključa nešto na TV-u.
            item.locked?.let { locks ->
                fun clean(names: List<String>) =
                    names.asSequence().filter { it.isNotEmpty() && it.length <= MAX_TEXT }.take(MAX_FAVORITES).toList()
                settings.addLockedCategories(
                    playlistId,
                    mapOf("live" to clean(locks.live), "movies" to clean(locks.movies), "series" to clean(locks.series)),
                )
            }
    }

    /** Deo paketa koji postoji samo u izvozu u fajl; pri prenosu sa telefona je prazan. */
    private suspend fun applyExtras(playlistId: Long, item: TransferPlaylist) {
        item.lockedItems.asSequence()
            .filter { it.kind in LOCK_KINDS && it.key.isNotEmpty() && it.key.length <= MAX_TEXT && it.name.length <= MAX_TEXT }
            .take(MAX_FAVORITES)
            .forEach { locks.setLocked(LockRepository.itemKey(it.kind, playlistId, it.key), true, it.name) }
        // Noviji zapis pobeđuje: ono što je na uređaju gledano posle izvoza ostaje.
        val knownHistory = db.historyDao().byPlaylist(playlistId).associate { it.channelKey to it.watchedAt }
        item.history.asSequence()
            .filter { it.key.isNotEmpty() && it.key.length <= MAX_TEXT && it.at > (knownHistory[it.key] ?: 0) }
            .take(MAX_HISTORY)
            .forEach { db.historyDao().upsert(HistoryEntity(playlistId, it.key, it.at)) }
        val knownProgress = db.watchProgressDao().byPlaylist(playlistId).associate { (it.kind to it.itemId) to it.updatedAt }
        val progress = item.progress.asSequence()
            .filter { it.kind in PROGRESS_KINDS && (it.title?.length ?: 0) <= MAX_TEXT && (it.poster?.length ?: 0) <= MAX_TEXT }
            .filter { it.updatedAt > (knownProgress[it.kind to it.itemId] ?: Long.MIN_VALUE) }
            .take(MAX_PROGRESS)
            .map {
                WatchProgressEntity(
                    playlistId = playlistId, kind = it.kind, itemId = it.itemId, seriesId = it.seriesId,
                    positionMs = it.positionMs.coerceAtLeast(0), durationMs = it.durationMs.coerceAtLeast(0),
                    updatedAt = it.updatedAt, title = it.title, poster = it.poster,
                    extension = it.extension?.take(MAX_LANGUAGE_LENGTH), watchedAt = it.watchedAt, dismissedAt = it.dismissedAt,
                )
            }
            .toList()
        progress.chunked(PROGRESS_CHUNK).forEach { db.watchProgressDao().upsertAll(it) }
        val now = System.currentTimeMillis()
        item.reminders.asSequence()
            .filter { it.startMs > now && it.channelKey.isNotEmpty() && it.channelKey.length <= MAX_TEXT }
            .filter { it.channelName.length <= MAX_TEXT && it.title.length <= MAX_TEXT }
            .take(MAX_REMINDERS)
            .forEach { reminders.add(Reminder(playlistId, it.channelKey, it.startMs, it.channelName, it.title)) }
    }

    /**
     * Sekcije omiljenih (u paketu se zovu liste). Sekcija sa istim nazivom se na TV-u dopunjuje, nova se
     * pravi; ništa se ne briše. Kanal iz paketa ide u omiljene i u sekciju iz paketa (izlazi iz one u
     * kojoj je bio); kad ga paket stavlja u više sekcija (starija verzija), važi prva.
     */
    private suspend fun importFavoriteLists(playlistId: Long, lists: List<TransferFavoriteList>, now: Long) {
        val dao = db.favoriteListDao()
        val placed = HashSet<String>()
        lists.asSequence()
            .map { it.copy(name = it.name.trim()) }
            .filter { it.name.isNotEmpty() && it.name.length <= MAX_LIST_NAME }
            .take(MAX_FAVORITE_LISTS)
            .forEach { list ->
                val listId = dao.byName(list.name)?.id
                    ?: dao.insert(FavoriteListEntity(name = list.name, position = dao.nextPosition()))
                list.channels.asSequence().filter { it.isNotEmpty() && it.length <= MAX_TEXT }.take(MAX_FAVORITES)
                    .forEachIndexed { position, key ->
                        if (!placed.add(key)) return@forEachIndexed
                        db.favoriteDao().insert(FavoriteEntity(playlistId, key, now + position))
                        dao.deleteItems(playlistId, key)
                        dao.insertItem(FavoriteListItemEntity(listId, playlistId, key, now + position))
                    }
            }
    }

    private suspend fun adoptOpenSubtitles(login: TransferLogin): Boolean {
        if (login.username.isBlank() || login.username.length > MAX_ACCOUNT_FIELD || login.password.length > MAX_ACCOUNT_FIELD) {
            return false
        }
        // Prijava ide preko interneta; telefon na nju ne čeka duže od nekoliko sekundi.
        return withTimeoutOrNull(ACCOUNT_WAIT_MS) { openSubtitles.adoptLogin(login.username, login.password) } ?: false
    }

    private suspend fun adoptTrakt(login: TransferTraktLogin): Boolean {
        if (login.accessToken.isBlank() || login.refreshToken.isBlank()) return false
        if (login.accessToken.length > MAX_ACCOUNT_FIELD || login.refreshToken.length > MAX_ACCOUNT_FIELD) return false
        return try {
            trakt.adoptLogin(TokenResponse(login.accessToken, login.refreshToken, login.expiresIn, login.createdAt))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Lista iz fajla nema adresu koja važi na oba uređaja, pa se prepoznaje po nazivu. */
    private fun PlaylistEntity.matches(type: PlaylistType, item: TransferPlaylist): Boolean = when {
        this.type != type -> false
        type == PlaylistType.M3U_FILE -> name.trim().equals(item.name.trim(), ignoreCase = true)
        else -> source.trimEnd('/').equals(item.source.trim().trimEnd('/'), ignoreCase = true) &&
            username.orEmpty() == item.username.trim()
    }

    private fun TransferPlaylist.sameSource(other: TransferPlaylist): Boolean =
        if (type == TransferPlaylist.TYPE_FILE) {
            name.trim().equals(other.name.trim(), ignoreCase = true)
        } else {
            source.trim().trimEnd('/').equals(other.source.trim().trimEnd('/'), ignoreCase = true) &&
                username.trim() == other.username.trim()
        }

    /** Null kad je sadržaj liste iz fajla neispravan. Za listu iz fajla sadržaj se prvo čuva na uređaju. */
    private suspend fun TransferPlaylist.toDraft(type: PlaylistType): PlaylistDraft? {
        val source = if (type == PlaylistType.M3U_FILE) {
            val content = fileContent?.let(BackupCodec::unpack)?.takeIf { it.size <= MAX_FILE_LIST_BYTES } ?: return null
            playlists.storeImportedFile(content)
        } else {
            this.source
        }
        return draftWith(type, source)
    }

    private fun TransferPlaylist.draftWith(type: PlaylistType, source: String) = PlaylistDraft(
        type = type,
        name = name,
        source = source,
        username = username,
        password = password,
        userAgent = userAgent,
        epgUrl = epgUrl,
        epgShiftHours = epgShiftHours,
    )

    private suspend fun applyCategories(playlistId: Long, categories: List<TransferCategory>) {
        if (categories.isEmpty()) return
        val byKey = db.categoryDao().getByPlaylist(playlistId).associateBy { it.type.name to it.name }
        for (category in categories) {
            val local = byKey[category.type to category.name] ?: continue
            if (category.hidden) db.categoryDao().setHidden(local.id, true)
            category.order?.let { db.categoryDao().setUserOrder(local.id, it) }
        }
    }

    private fun TransferVodCategories.toPrefs(): VodCategoryPrefs {
        // ID kategorije ne sme da sadrži znak kojim se spisak razdvaja u podešavanjima.
        fun clean(ids: List<String>) =
            ids.asSequence().filter { it.isNotEmpty() && it.length <= MAX_LANGUAGE_LENGTH && '\n' !in it }.take(MAX_FAVORITES).toList()
        return VodCategoryPrefs(clean(hiddenMovies), clean(hiddenSeries), clean(orderMovies), clean(orderSeries))
    }

    private suspend fun applySettings(incoming: TransferSettings) {
        settings.setResumeLastChannel(incoming.resumeLastChannel)
        BufferSize.entries.firstOrNull { it.name == incoming.buffer }?.let { settings.setPlayerBuffer(it) }
        fun slots(list: List<String>?, single: String) =
            (list ?: listOf(single)).take(PlayerSettings.LANGUAGE_SLOTS).map { it.take(MAX_LANGUAGE_LENGTH) }
        settings.setAudioLanguageSlots(slots(incoming.audioLanguages, incoming.audioLanguage))
        settings.setSubtitleLanguageSlots(slots(incoming.subtitleLanguages, incoming.subtitleLanguage))
        incoming.seekStepSeconds?.takeIf { it in PlayerSettings.SEEK_STEP_OPTIONS }?.let { settings.setSeekStep(it) }
        incoming.subtitleBackground?.let { settings.setSubtitleBackground(it) }
        SubtitleSize.entries.firstOrNull { it.name == incoming.subtitleSize }?.let { settings.setSubtitleSize(it) }
        SubtitlePosition.entries.firstOrNull { it.name == incoming.subtitlePosition }?.let { settings.setSubtitlePosition(it) }
        LoudnessLevel.entries.firstOrNull { it.name == incoming.loudness }?.let { settings.setLoudness(it) }
        incoming.autoRefreshPlaylists?.let { settings.setAutoRefreshPlaylists(it) }
        // Nepoznato ime boje aplikacija sama zamenjuje podrazumevanom.
        incoming.accentColor?.takeIf { ENUM_NAME.matches(it) }?.let { settings.setAccentColor(it) }
        VodSort.entries.firstOrNull { it.name == incoming.vodSortMovies }?.let { settings.setVodSort(series = false, it.name) }
        VodSort.entries.firstOrNull { it.name == incoming.vodSortSeries }?.let { settings.setVodSort(series = true, it.name) }
    }

    private companion object {
        const val MAX_FAVORITES = 5_000
        const val ACCOUNT_WAIT_MS = 5_000L
        const val MAX_FAVORITE_LISTS = 100
        const val MAX_LIST_NAME = 100
        const val MAX_LANGUAGE_LENGTH = 16
        const val MAX_ACCOUNT_FIELD = 2_000
        const val MAX_TEXT = 2_000
        const val MAX_HISTORY = 1_000
        const val MAX_PROGRESS = 50_000
        const val PROGRESS_CHUNK = 500
        const val MAX_REMINDERS = 500

        /** Veća lista iz fajla ne ulazi u izvoz. */
        const val MAX_FILE_LIST_BYTES = 20 * 1024 * 1024
        val LOCK_KINDS = setOf(LockRepository.KIND_CHANNEL, LockRepository.KIND_MOVIE, LockRepository.KIND_SERIES)
        val PROGRESS_KINDS = setOf("movie", "episode")
        val VOD_KINDS = setOf("movie", "series")
        val ENUM_NAME = Regex("[A-Z_]{1,32}")

        /** Oblik zapisa iz `Pin.hash`: dodatak i otisak, oba heksadecimalno. */
        val PIN_RECORD = Regex("[0-9a-f]{1,64}:[0-9a-f]{64}")
    }
}

/**
 * Prijem čiji je brzi deo gotov. [result] je ono što se odmah javlja pošiljaocu ([queued] novih
 * lista se tek učitava), a [finish] ih preuzima i vraća konačan ishod.
 */
class PendingImport(
    val queued: Int,
    private val base: TransferResult,
    private val load: suspend (onProgress: (Int, Int) -> Unit) -> TransferResult,
) {
    val result: TransferResult get() = base.copy(loading = queued)

    suspend fun finish(onProgress: (Int, Int) -> Unit = { _, _ -> }): TransferResult =
        if (queued == 0) base else load(onProgress)
}
