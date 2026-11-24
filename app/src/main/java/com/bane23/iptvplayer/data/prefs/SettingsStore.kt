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
import com.bane23.iptvplayer.domain.RemoteAction
import com.bane23.iptvplayer.domain.RemoteButton
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.LoudnessLevel
import com.bane23.iptvplayer.player.PlayerSettings
import com.bane23.iptvplayer.player.SubtitlePosition
import com.bane23.iptvplayer.player.SubtitleSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Uređenje kategorija filmova i serija jedne liste, po ID-jevima kategorija sa servera. */
data class VodCategoryPrefs(
    val hiddenMovies: List<String> = emptyList(),
    val hiddenSeries: List<String> = emptyList(),
    val orderMovies: List<String> = emptyList(),
    val orderSeries: List<String> = emptyList(),
)

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

    /**
     * Štednja mobilnih podataka (samo telefon): na mobilnom internetu se pušta verzija kanala od 720p
     * kad postoji, a pregled kanala u spisku ostaje bez slike. Uključeno dok korisnik ne isključi.
     */
    val dataSaver: Flow<Boolean> =
        dataStore.data.map { it[DATA_SAVER] ?: true }.distinctUntilChanged()

    suspend fun setDataSaver(enabled: Boolean) {
        dataStore.edit { it[DATA_SAVER] = enabled }
    }

    /** Da li Uživo na TV-u pušta mali pregled (bez zvuka) kanala na kome je fokus. */
    val tvChannelPreview: Flow<Boolean> =
        dataStore.data.map { it[TV_CHANNEL_PREVIEW] ?: true }.distinctUntilChanged()

    suspend fun setTvChannelPreview(enabled: Boolean) {
        dataStore.edit { it[TV_CHANNEL_PREVIEW] = enabled }
    }

    /** Da li Filmovi i Serije na TV-u iznad postera prikazuju opis filma ili serije na kojoj je fokus. */
    val tvVodPreview: Flow<Boolean> =
        dataStore.data.map { it[TV_VOD_PREVIEW] ?: true }.distinctUntilChanged()

    suspend fun setTvVodPreview(enabled: Boolean) {
        dataStore.edit { it[TV_VOD_PREVIEW] = enabled }
    }

    /** Da li telefon u landscape-u crta sadržaj do ivice ekrana, i preko isečka za kameru. */
    val drawUnderCutout: Flow<Boolean> =
        dataStore.data.map { it[DRAW_UNDER_CUTOUT] ?: true }.distinctUntilChanged()

    suspend fun setDrawUnderCutout(enabled: Boolean) {
        dataStore.edit { it[DRAW_UNDER_CUTOUT] = enabled }
    }

    /** Da li je aplikacija na telefonu preko celog ekrana (bez gornje i donje sistemske trake). */
    val appFullScreen: Flow<Boolean> =
        dataStore.data.map { it[APP_FULL_SCREEN] ?: true }.distinctUntilChanged()

    suspend fun setAppFullScreen(enabled: Boolean) {
        dataStore.edit { it[APP_FULL_SCREEN] = enabled }
    }

    /** Telefon: prevlačenje uz levu ivicu plejera menja osvetljenje. Uključeno dok ga korisnik ne isključi. */
    val playerBrightnessSwipe: Flow<Boolean> =
        dataStore.data.map { it[PLAYER_BRIGHTNESS_SWIPE] ?: true }.distinctUntilChanged()

    suspend fun setPlayerBrightnessSwipe(enabled: Boolean) {
        dataStore.edit { it[PLAYER_BRIGHTNESS_SWIPE] = enabled }
    }

    /** Telefon: prevlačenje uz desnu ivicu plejera menja jačinu zvuka. Uključeno dok ga korisnik ne isključi. */
    val playerVolumeSwipe: Flow<Boolean> =
        dataStore.data.map { it[PLAYER_VOLUME_SWIPE] ?: true }.distinctUntilChanged()

    suspend fun setPlayerVolumeSwipe(enabled: Boolean) {
        dataStore.edit { it[PLAYER_VOLUME_SWIPE] = enabled }
    }

    /** Telefon: zvuk nastavlja kad se ekran ugasi ili aplikacija ode u pozadinu. Isključeno dok korisnik ne uključi. */
    val backgroundPlay: Flow<Boolean> =
        dataStore.data.map { it[BACKGROUND_PLAY] ?: false }.distinctUntilChanged()

    suspend fun setBackgroundPlay(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_PLAY] = enabled }
    }

    /** Da li se liste same osvežavaju jednom dnevno u pozadini. Uključeno dok korisnik ne isključi. */
    val autoRefreshPlaylists: Flow<Boolean> =
        dataStore.data.map { it[AUTO_REFRESH_PLAYLISTS] ?: true }.distinctUntilChanged()

    suspend fun setAutoRefreshPlaylists(enabled: Boolean) {
        dataStore.edit { it[AUTO_REFRESH_PLAYLISTS] = enabled }
    }

    /**
     * Da li se spiskovi filmova i serija čuvaju na uređaju, da se Filmovi i Serije otvaraju odmah.
     * Uključeno dok korisnik ne isključi.
     */
    val vodLocalCache: Flow<Boolean> =
        dataStore.data.map { it[VOD_LOCAL_CACHE] ?: true }.distinctUntilChanged()

    suspend fun setVodLocalCache(enabled: Boolean) {
        dataStore.edit { it[VOD_LOCAL_CACHE] = enabled }
    }

    /** TV: osvežavanje ekrana se usklađuje sa brojem sličica snimka dok je plejer otvoren. */
    val matchFrameRate: Flow<Boolean> = dataStore.data.map { it[MATCH_FRAME_RATE] ?: false }.distinctUntilChanged()

    suspend fun setMatchFrameRate(enabled: Boolean) {
        dataStore.edit { it[MATCH_FRAME_RATE] = enabled }
    }

    /** TV: započeti filmovi i serije se vide u redu „Nastavite sa gledanjem” na početnom ekranu uređaja. */
    val watchNext: Flow<Boolean> = dataStore.data.map { it[WATCH_NEXT] ?: true }.distinctUntilChanged()

    suspend fun setWatchNext(enabled: Boolean) {
        dataStore.edit { it[WATCH_NEXT] = enabled }
    }

    /** Da li se aplikacija sama otvara kad se uređaj upali (za TV box). */
    val autostart: Flow<Boolean> = dataStore.data.map { it[AUTOSTART] ?: false }.distinctUntilChanged()

    suspend fun setAutostart(enabled: Boolean) {
        dataStore.edit { it[AUTOSTART] = enabled }
    }

    /** Posle ažuriranja iz aplikacije nova verzija se sama ponovo otvara (gde sistem dozvoli). */
    val reopenAfterUpdate: Flow<Boolean> =
        dataStore.data.map { it[REOPEN_AFTER_UPDATE] ?: true }.distinctUntilChanged()

    suspend fun setReopenAfterUpdate(enabled: Boolean) {
        dataStore.edit { it[REOPEN_AFTER_UPDATE] = enabled }
    }

    /** Ključ ovog TV-a za slanje sa telefona (cast na ovu aplikaciju); null dok se ne napravi. */
    val castKey: Flow<String?> = dataStore.data.map { it[CAST_KEY] }.distinctUntilChanged()

    suspend fun setCastKey(key: String) {
        dataStore.edit { it[CAST_KEY] = key }
    }

    /** Folder u koji idu novi snimci; prazno = memorija uređaja. Važi samo za ovaj uređaj. */
    val recordingDir: Flow<String> = dataStore.data.map { it[RECORDING_DIR].orEmpty() }.distinctUntilChanged()

    suspend fun setRecordingDir(path: String) {
        dataStore.edit { it[RECORDING_DIR] = path }
    }

    /** Na telefonu: ključevi TV-a sa kojima je telefon već povezan, po imenu TV-a. */
    val castPeers: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[CAST_PEERS].orEmpty().mapNotNull { entry ->
            val name = entry.substringBeforeLast(PEER_SEPARATOR, "")
            val key = entry.substringAfterLast(PEER_SEPARATOR, "")
            if (name.isEmpty() || key.isEmpty()) null else name to key
        }.toMap()
    }.distinctUntilChanged()

    suspend fun setCastPeer(name: String, key: String) {
        dataStore.edit { prefs ->
            val others = prefs[CAST_PEERS].orEmpty().filterNot { it.substringBeforeLast(PEER_SEPARATOR, "") == name }
            prefs[CAST_PEERS] = (others + "$name$PEER_SEPARATOR$key").toSet()
        }
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

    /** Zaključane kategorije liste po sekciji (`live`, `movies`, `series`), bez dela ključa sa listom. */
    suspend fun lockedCategoriesOf(playlistId: Long): Map<String, List<String>> {
        val keys = dataStore.data.first()[LOCKED_CATEGORIES].orEmpty()
        return LOCK_SECTIONS.associateWith { section ->
            val prefix = "$section|$playlistId|"
            keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.sorted()
        }
    }

    /** Dodaje zaključavanja listi; postojeća ostaju. Važe tek kad na uređaju postoji PIN. */
    suspend fun addLockedCategories(playlistId: Long, bySection: Map<String, List<String>>) {
        val added = bySection.filterKeys { it in LOCK_SECTIONS }
            .flatMap { (section, names) -> names.map { "$section|$playlistId|$it" } }
        if (added.isEmpty()) return
        dataStore.edit { it[LOCKED_CATEGORIES] = it[LOCKED_CATEGORIES].orEmpty() + added }
    }

    /**
     * Ključevi sakrivenih kategorija filmova i serija (isti oblik kao kod zaključavanja:
     * `movies|<lista>|<kategorija>`). Kategorije kanala se sakrivaju u bazi.
     */
    val hiddenVodCategories: Flow<Set<String>> =
        dataStore.data.map { it[HIDDEN_VOD_CATEGORIES] ?: emptySet() }.distinctUntilChanged()

    suspend fun setVodCategoriesHidden(keys: Collection<String>, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[HIDDEN_VOD_CATEGORIES] ?: emptySet()
            prefs[HIDDEN_VOD_CATEGORIES] = if (hidden) current + keys else current - keys.toSet()
        }
    }

    /** Redosled kategorija filmova ([series] = false) ili serija koji je zadao korisnik (ID-jevi sa servera). */
    fun vodCategoryOrder(playlistId: Long, series: Boolean): Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[vodOrderKey(playlistId, series)]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
        }.distinctUntilChanged()

    suspend fun setVodCategoryOrder(playlistId: Long, series: Boolean, ids: List<String>) {
        dataStore.edit { it[vodOrderKey(playlistId, series)] = ids.joinToString(LIST_SEPARATOR) }
    }

    /** Sakrivene kategorije i redosled kategorija filmova i serija jedne liste, za prenos na drugi uređaj. */
    suspend fun vodCategoryPrefs(playlistId: Long): VodCategoryPrefs {
        val prefs = dataStore.data.first()
        val hidden = prefs[HIDDEN_VOD_CATEGORIES].orEmpty()
        fun hiddenIds(prefix: String) = hidden.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.sorted()
        fun order(series: Boolean) =
            prefs[vodOrderKey(playlistId, series)]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
        return VodCategoryPrefs(
            hiddenMovies = hiddenIds("movies|$playlistId|"),
            hiddenSeries = hiddenIds("series|$playlistId|"),
            orderMovies = order(false),
            orderSeries = order(true),
        )
    }

    /** Zamenjuje sakrivene kategorije i redosled kategorija filmova i serija liste onim iz [incoming]. */
    suspend fun replaceVodCategoryPrefs(playlistId: Long, incoming: VodCategoryPrefs) {
        dataStore.edit { prefs ->
            val movies = "movies|$playlistId|"
            val series = "series|$playlistId|"
            val others = prefs[HIDDEN_VOD_CATEGORIES].orEmpty().filterNot { it.startsWith(movies) || it.startsWith(series) }
            prefs[HIDDEN_VOD_CATEGORIES] =
                (others + incoming.hiddenMovies.map { movies + it } + incoming.hiddenSeries.map { series + it }).toSet()
            for ((isSeries, ids) in listOf(false to incoming.orderMovies, true to incoming.orderSeries)) {
                val key = vodOrderKey(playlistId, isSeries)
                if (ids.isEmpty()) prefs.remove(key) else prefs[key] = ids.joinToString(LIST_SEPARATOR)
            }
        }
    }

    /** Podsetnici za emisije, svaki kao jedan zapis (vidi `Reminder.encode`). */
    val reminders: Flow<Set<String>> = dataStore.data.map { it[REMINDERS].orEmpty() }.distinctUntilChanged()

    suspend fun editReminders(transform: (Set<String>) -> Set<String>) {
        dataStore.edit { it[REMINDERS] = transform(it[REMINDERS].orEmpty()) }
    }

    /** Redosled filmova, odnosno serija, u kategoriji (naziv iz `VodSort`). */
    fun vodSort(series: Boolean): Flow<String?> =
        dataStore.data.map { it[vodSortKey(series)] }.distinctUntilChanged()

    suspend fun setVodSort(series: Boolean, value: String) {
        dataStore.edit { it[vodSortKey(series)] = value }
    }

    /** Filter filmova, odnosno serija (zapis iz `VodFilter.encode`); važi dok ga korisnik ne poništi. */
    fun vodFilter(series: Boolean): Flow<String?> =
        dataStore.data.map { it[vodFilterKey(series)] }.distinctUntilChanged()

    suspend fun setVodFilter(series: Boolean, value: String) {
        dataStore.edit { it[vodFilterKey(series)] = value }
    }

    private fun vodFilterKey(series: Boolean) = stringPreferencesKey(if (series) "vod_filter_series" else "vod_filter_movies")

    private fun vodSortKey(series: Boolean) = stringPreferencesKey(if (series) "vod_sort_series" else "vod_sort_movies")

    /**
     * Kategorija koja je poslednja bila otvorena u sekciji [section] (`live`, `movies`, `series`)
     * liste [playlistId], da se sekcija sledeći put otvori na njoj.
     */
    fun lastCategory(playlistId: Long, section: String): Flow<String?> =
        dataStore.data.map { it[lastCategoryKey(playlistId, section)] }.distinctUntilChanged()

    suspend fun setLastCategory(playlistId: Long, section: String, value: String) {
        dataStore.edit { it[lastCategoryKey(playlistId, section)] = value }
    }

    /** Briše sve što je zapamćeno o kategorijama obrisane liste. */
    suspend fun clearPlaylistCategoryPrefs(playlistId: Long) {
        dataStore.edit { prefs ->
            for (series in listOf(false, true)) prefs.remove(vodOrderKey(playlistId, series))
            for (section in listOf("live", "movies", "series")) prefs.remove(lastCategoryKey(playlistId, section))
            val hidden = prefs[HIDDEN_VOD_CATEGORIES]
            if (hidden != null) {
                prefs[HIDDEN_VOD_CATEGORIES] = hidden.filterNot { key ->
                    key.startsWith("movies|$playlistId|") || key.startsWith("series|$playlistId|")
                }.toSet()
            }
        }
    }

    private fun vodOrderKey(playlistId: Long, series: Boolean) =
        stringPreferencesKey("vod_category_order_${if (series) "series" else "movies"}_$playlistId")

    private fun lastCategoryKey(playlistId: Long, section: String) =
        stringPreferencesKey("last_category_${section}_$playlistId")

    /** Da li preuzimanje filmova i epizoda sme da ide i preko mobilnih podataka; inače samo preko Wi-Fi mreže. */
    val downloadOnMobile: Flow<Boolean> = dataStore.data.map { it[DOWNLOAD_ON_MOBILE] ?: false }.distinctUntilChanged()

    suspend fun setDownloadOnMobile(enabled: Boolean) {
        dataStore.edit { it[DOWNLOAD_ON_MOBILE] = enabled }
    }

    /** Šta koje dugme daljinskog radi u plejeru kanala na TV-u; nepodešena dugmad imaju podrazumevanu radnju. */
    val remoteButtons: Flow<Map<RemoteButton, RemoteAction>> = dataStore.data.map { prefs ->
        RemoteButton.entries.associateWith { RemoteAction.of(prefs[remoteButtonKey(it)], it) }
    }.distinctUntilChanged()

    suspend fun setRemoteButton(button: RemoteButton, action: RemoteAction) {
        dataStore.edit { it[remoteButtonKey(button)] = action.name }
    }

    private fun remoteButtonKey(button: RemoteButton) = stringPreferencesKey("remote_button_${button.name.lowercase()}")

    /** Pravila za kanale; sva su isključena dok ih korisnik ne uključi. */
    val channelRules: Flow<com.bane23.iptvplayer.domain.ChannelRuleSettings> = dataStore.data.map { prefs ->
        com.bane23.iptvplayer.domain.ChannelRuleSettings(
            hideSd = prefs[RULE_HIDE_SD] ?: false,
            hidePlusOne = prefs[RULE_HIDE_PLUS_ONE] ?: false,
            mergeSameName = prefs[RULE_MERGE_SAME_NAME] ?: false,
        )
    }.distinctUntilChanged()

    /** Filter kanala u Uživo (kvalitet i zemlja); važi dok ga korisnik ne poništi. */
    val liveFilter: Flow<com.bane23.iptvplayer.domain.ChannelFilter> = dataStore.data.map { prefs ->
        com.bane23.iptvplayer.domain.ChannelFilter(
            minQuality = (prefs[LIVE_FILTER_QUALITY] ?: 0).coerceIn(0, 3),
            country = prefs[LIVE_FILTER_COUNTRY]?.takeIf { it.isNotBlank() },
        )
    }.distinctUntilChanged()

    suspend fun setLiveFilter(filter: com.bane23.iptvplayer.domain.ChannelFilter) {
        dataStore.edit { prefs ->
            prefs[LIVE_FILTER_QUALITY] = filter.minQuality
            val country = filter.country
            if (country == null) prefs.remove(LIVE_FILTER_COUNTRY) else prefs[LIVE_FILTER_COUNTRY] = country
        }
    }

    suspend fun setRuleHideSd(enabled: Boolean) {
        dataStore.edit { it[RULE_HIDE_SD] = enabled }
    }

    suspend fun setRuleHidePlusOne(enabled: Boolean) {
        dataStore.edit { it[RULE_HIDE_PLUS_ONE] = enabled }
    }

    suspend fun setRuleMergeSameName(enabled: Boolean) {
        dataStore.edit { it[RULE_MERGE_SAME_NAME] = enabled }
    }

    /** Serije: sledeća epizoda kreće sama kad se tekuća završi. Isključeno: kartica je samo nudi. */
    val autoNextEpisode: Flow<Boolean> = dataStore.data.map { it[AUTO_NEXT_EPISODE] ?: true }.distinctUntilChanged()

    suspend fun setAutoNextEpisode(enabled: Boolean) {
        dataStore.edit { it[AUTO_NEXT_EPISODE] = enabled }
    }

    /** Ekran pauze: posle nekoliko sekundi pauze naslov, opis i sat umesto kontrola. */
    val pauseInfo: Flow<Boolean> = dataStore.data.map { it[PAUSE_INFO] ?: true }.distinctUntilChanged()

    suspend fun setPauseInfo(enabled: Boolean) {
        dataStore.edit { it[PAUSE_INFO] = enabled }
    }

    /** Crna tema: potpuno crna pozadina umesto tamnosive, bez sjaja u uglu. */
    val blackTheme: Flow<Boolean> = dataStore.data.map { it[BLACK_THEME] ?: false }.distinctUntilChanged()

    suspend fun setBlackTheme(enabled: Boolean) {
        dataStore.edit { it[BLACK_THEME] = enabled }
    }

    /**
     * Klasičan izgled, bez stakla: neprovidne pločice i ravna pozadina. Isključen dok ga korisnik ne
     * uključi, na telefonu i na TV-u (Bane, 23. 11. 2026: merenje na projektoru je pokazalo da „Staklo”
     * nije sporije, pa TV i telefon izgledaju isto).
     */
    val classicLook: Flow<Boolean> = dataStore.data.map { it[CLASSIC_LOOK] ?: false }.distinctUntilChanged()

    suspend fun setClassicLook(enabled: Boolean) {
        dataStore.edit { it[CLASSIC_LOOK] = enabled }
    }

    /** Ime teme aplikacije koju je korisnik izabrao (vidi `AppTheme`), ili null za podrazumevanu. */
    val appTheme: Flow<String?> = dataStore.data.map { it[APP_THEME] }.distinctUntilChanged()

    suspend fun setAppTheme(name: String) {
        dataStore.edit { it[APP_THEME] = name }
    }

    /**
     * Bočni meni na TV-u samo sa ikonicama (strelica na vrhu menija). Inače se meni raširi kad fokus
     * pređe na njega i pomeri sadržaj udesno.
     */
    val tvRailIconsOnly: Flow<Boolean> = dataStore.data.map { it[TV_RAIL_ICONS_ONLY] ?: false }.distinctUntilChanged()

    suspend fun setTvRailIconsOnly(enabled: Boolean) {
        dataStore.edit { it[TV_RAIL_ICONS_ONLY] = enabled }
    }

    /** Nedavni upiti zajedničke pretrage, najnoviji prvi (vidi [RecentSearches]). */
    val recentSearches: Flow<List<String>> =
        dataStore.data.map { RecentSearches.decode(it[RECENT_SEARCHES]) }.distinctUntilChanged()

    suspend fun addRecentSearch(query: String) {
        dataStore.edit { it[RECENT_SEARCHES] = RecentSearches.encode(RecentSearches.add(RecentSearches.decode(it[RECENT_SEARCHES]), query)) }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    /** Ime akcentne boje koju je korisnik izabrao (vidi `AccentColor`), ili null za podrazumevanu. */
    val accentColor: Flow<String?> = dataStore.data.map { it[ACCENT_COLOR] }.distinctUntilChanged()

    suspend fun setAccentColor(name: String) {
        dataStore.edit { it[ACCENT_COLOR] = name }
    }

    /** Ime veličine prikaza koju je korisnik izabrao (vidi `UiScale`), ili null za podrazumevanu. */
    val uiScale: Flow<String?> = dataStore.data.map { it[UI_SCALE] }.distinctUntilChanged()

    /** Posle koliko minuta bez tastera se na TV-u pali čuvar ekrana; 0 znači isključeno. */
    val screensaverMinutes: Flow<Int> = dataStore.data
        .map { ScreensaverTimeout.fromStored(it[SCREENSAVER_MINUTES]) }
        .distinctUntilChanged()

    suspend fun setScreensaverMinutes(minutes: Int) {
        dataStore.edit { it[SCREENSAVER_MINUTES] = minutes }
    }

    /** Grad za temperaturu u čuvaru ekrana; prazno znači da se mesto pogađa po IP adresi. */
    val weatherCity: Flow<String> = dataStore.data.map { it[WEATHER_CITY].orEmpty() }.distinctUntilChanged()

    suspend fun setWeatherCity(city: String) {
        dataStore.edit { it[WEATHER_CITY] = city.trim() }
    }

    suspend fun setUiScale(name: String) {
        dataStore.edit { it[UI_SCALE] = name }
    }

    /**
     * Oznaka kanala ažuriranja koji je korisnik izabrao (vidi `UpdateChannel`), ili null dok nije birao
     * (tada važi kanal iz koga je build).
     */
    val updateChannel: Flow<String?> = dataStore.data.map { it[UPDATE_CHANNEL] }.distinctUntilChanged()

    suspend fun setUpdateChannel(tag: String) {
        dataStore.edit { it[UPDATE_CHANNEL] = tag }
    }

    /** Telefon: položaj ekrana cele aplikacije (ime vrednosti iz ScreenPosition); null znači da prati telefon. */
    val screenPosition: Flow<String?> = dataStore.data.map { it[SCREEN_POSITION] }.distinctUntilChanged()

    suspend fun setScreenPosition(name: String) {
        dataStore.edit { it[SCREEN_POSITION] = name }
    }

    /** Telefon: poruka u plejeru da postoji položen prikaz je već prikazana (prikazuje se samo prvi put). */
    val rotateHintShown: Flow<Boolean> =
        dataStore.data.map { it[ROTATE_HINT_SHOWN] ?: false }.distinctUntilChanged()

    suspend fun setRotateHintShown() {
        dataStore.edit { it[ROTATE_HINT_SHOWN] = true }
    }

    /** Uputstvo na TV-u kako se kanal dodaje u omiljene je već prikazano (prikazuje se samo prvi put). */
    val tvFavoriteHintShown: Flow<Boolean> =
        dataStore.data.map { it[TV_FAVORITE_HINT_SHOWN] ?: false }.distinctUntilChanged()

    suspend fun setTvFavoriteHintShown() {
        dataStore.edit { it[TV_FAVORITE_HINT_SHOWN] = true }
    }

    /** Podešavanja plejera. Čitaju se pri otvaranju plejera, pa izmena važi od sledećeg puštanja. */
    val playerSettings: Flow<PlayerSettings> = dataStore.data.map { prefs ->
        PlayerSettings(
            buffer = BufferSize.entries.firstOrNull { it.name == prefs[PLAYER_BUFFER] } ?: BufferSize.NORMAL,
            decoder = DecoderMode.entries.firstOrNull { it.name == prefs[PLAYER_DECODER] } ?: DecoderMode.AUTO,
            audioLanguageSlots = PlayerSettings.parseLanguageSlots(prefs[PLAYER_AUDIO_LANGUAGE]),
            subtitleLanguageSlots = PlayerSettings.parseLanguageSlots(prefs[PLAYER_SUBTITLE_LANGUAGE]),
            seekStepSeconds = prefs[PLAYER_SEEK_STEP]?.takeIf { it in PlayerSettings.SEEK_STEP_OPTIONS }
                ?: PlayerSettings.DEFAULT_SEEK_STEP_SECONDS,
            subtitleBackground = prefs[SUBTITLE_BACKGROUND] ?: true,
            subtitleSize = SubtitleSize.entries.firstOrNull { it.name == prefs[SUBTITLE_SIZE] } ?: SubtitleSize.NORMAL,
            subtitlePosition = SubtitlePosition.entries.firstOrNull { it.name == prefs[SUBTITLE_POSITION] }
                ?: SubtitlePosition.LOW,
            loudness = LoudnessLevel.entries.firstOrNull { it.name == prefs[PLAYER_LOUDNESS] } ?: PlayerSettings().loudness,
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

    suspend fun setLoudness(level: LoudnessLevel) {
        dataStore.edit { it[PLAYER_LOUDNESS] = level.name }
    }

    /** Jezik zvuka na mestu [slot] (0 = prvi po važnosti); prazno prazni to mesto. */
    suspend fun setAudioLanguage(slot: Int, language: String) = setLanguageSlot(PLAYER_AUDIO_LANGUAGE, slot, language)

    /** Jezik titlova na mestu [slot] (0 = prvi po važnosti); prazno prazni to mesto. */
    suspend fun setSubtitleLanguage(slot: Int, language: String) = setLanguageSlot(PLAYER_SUBTITLE_LANGUAGE, slot, language)

    suspend fun setAudioLanguageSlots(slots: List<String>) {
        dataStore.edit { it[PLAYER_AUDIO_LANGUAGE] = PlayerSettings.formatLanguageSlots(slots) }
    }

    suspend fun setSubtitleLanguageSlots(slots: List<String>) {
        dataStore.edit { it[PLAYER_SUBTITLE_LANGUAGE] = PlayerSettings.formatLanguageSlots(slots) }
    }

    private suspend fun setLanguageSlot(key: Preferences.Key<String>, slot: Int, language: String) {
        dataStore.edit { prefs ->
            val slots = PlayerSettings.withLanguageSlot(PlayerSettings.parseLanguageSlots(prefs[key]), slot, language)
            prefs[key] = PlayerSettings.formatLanguageSlots(slots)
        }
    }

    /**
     * Sva podešavanja koja idu u izvoz u fajl, kao tekst po ključu: prvo opšta, pa ona vezana za
     * uređaj. Ono što korisnik nije menjao se ne izvozi, pa posle uvoza važi podrazumevano.
     */
    suspend fun exportPrefs(): Pair<Map<String, String>, Map<String, String>> {
        val prefs = dataStore.data.first()
        fun collect(keys: List<Preferences.Key<*>>): Map<String, String> = buildMap {
            for (key in keys) {
                when (val value = prefs[key]) {
                    null -> Unit
                    is Set<*> -> put(key.name, value.joinToString(SET_SEPARATOR))
                    else -> put(key.name, value.toString())
                }
            }
        }
        return collect(GENERAL_KEYS + remoteButtonKeys()) to collect(DEVICE_KEYS)
    }

    /**
     * Upisuje podešavanja iz izvoza. Nepoznati ključevi i vrednosti pogrešne vrste se preskaču. Uz
     * [replace] se podešavanja kojih u izvozu nema vraćaju na podrazumevano; [device] je null kad
     * izvoz potiče sa druge vrste uređaja, pa se podešavanja vezana za uređaj ne diraju.
     */
    suspend fun importPrefs(general: Map<String, String>, device: Map<String, String>?, replace: Boolean) {
        dataStore.edit { prefs ->
            fun apply(keys: List<Preferences.Key<*>>, values: Map<String, String>) {
                for (key in keys) {
                    val text = values[key.name]?.takeIf { it.length <= MAX_IMPORTED_VALUE }
                    if (text == null) {
                        if (replace) prefs.remove(key)
                        continue
                    }
                    storeText(prefs, key, text)
                }
            }
            apply(GENERAL_KEYS + remoteButtonKeys(), general)
            if (device != null) apply(DEVICE_KEYS, device)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun storeText(prefs: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<*>, text: String) {
        when (key) {
            in BOOLEAN_KEYS, CLASSIC_LOOK -> text.toBooleanStrictOrNull()?.let { prefs[key as Preferences.Key<Boolean>] = it }
            in INT_KEYS -> text.toIntOrNull()?.let { prefs[key as Preferences.Key<Int>] = it }
            in SET_KEYS -> prefs[key as Preferences.Key<Set<String>>] = text.split(SET_SEPARATOR).filter { it.isNotEmpty() }.toSet()
            else -> prefs[key as Preferences.Key<String>] = text
        }
    }

    private fun remoteButtonKeys(): List<Preferences.Key<*>> = RemoteButton.entries.map { remoteButtonKey(it) }

    /** Briše sva zaključavanja kategorija liste (zamena pri uvozu na uređaju bez PIN-a). */
    suspend fun clearPlaylistLocks(playlistId: Long) {
        dataStore.edit { prefs ->
            val locked = prefs[LOCKED_CATEGORIES] ?: return@edit
            prefs[LOCKED_CATEGORIES] = locked.filterNot { key -> LOCK_SECTIONS.any { key.startsWith("$it|$playlistId|") } }.toSet()
        }
    }

    private companion object {
        /** Razdvaja članove skupa u izvozu; ne pojavljuje se u vrednostima. */
        const val SET_SEPARATOR = "\u001E"
        const val MAX_IMPORTED_VALUE = 20_000

        val PLAYER_BUFFER = stringPreferencesKey("player_buffer")
        val PLAYER_DECODER = stringPreferencesKey("player_decoder")
        val PLAYER_SEEK_STEP = intPreferencesKey("player_seek_step")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val SUBTITLE_POSITION = stringPreferencesKey("subtitle_position")
        val PLAYER_LOUDNESS = stringPreferencesKey("player_loudness")
        val PLAYER_AUDIO_LANGUAGE = stringPreferencesKey("player_audio_language")
        val PLAYER_SUBTITLE_LANGUAGE = stringPreferencesKey("player_subtitle_language")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val TV_CHANNEL_PREVIEW = booleanPreferencesKey("tv_channel_preview")
        val TV_VOD_PREVIEW = booleanPreferencesKey("tv_vod_preview")
        val DRAW_UNDER_CUTOUT = booleanPreferencesKey("draw_under_cutout")
        val APP_FULL_SCREEN = booleanPreferencesKey("app_full_screen")
        val PLAYER_BRIGHTNESS_SWIPE = booleanPreferencesKey("player_brightness_swipe")
        val PLAYER_VOLUME_SWIPE = booleanPreferencesKey("player_volume_swipe")
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val REOPEN_AFTER_UPDATE = booleanPreferencesKey("reopen_after_update")
        val AUTO_REFRESH_PLAYLISTS = booleanPreferencesKey("auto_refresh_playlists")
        val VOD_LOCAL_CACHE = booleanPreferencesKey("vod_local_cache")
        val MATCH_FRAME_RATE = booleanPreferencesKey("match_frame_rate")
        val WATCH_NEXT = booleanPreferencesKey("watch_next")
        val BLACK_THEME = booleanPreferencesKey("black_theme")
        val CLASSIC_LOOK = booleanPreferencesKey("classic_look")
        val APP_THEME = stringPreferencesKey("app_theme")
        val SCREEN_POSITION = stringPreferencesKey("screen_position")
        val ROTATE_HINT_SHOWN = booleanPreferencesKey("rotate_hint_shown")
        val TV_RAIL_ICONS_ONLY = booleanPreferencesKey("tv_rail_icons_only")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val PAUSE_INFO = booleanPreferencesKey("pause_info")
        val AUTO_NEXT_EPISODE = booleanPreferencesKey("auto_next_episode")
        val LIVE_FILTER_QUALITY = intPreferencesKey("live_filter_quality")
        val LIVE_FILTER_COUNTRY = stringPreferencesKey("live_filter_country")
        val RULE_HIDE_SD = booleanPreferencesKey("rule_hide_sd")
        val RULE_HIDE_PLUS_ONE = booleanPreferencesKey("rule_hide_plus_one")
        val RULE_MERGE_SAME_NAME = booleanPreferencesKey("rule_merge_same_name")
        val DOWNLOAD_ON_MOBILE = booleanPreferencesKey("download_on_mobile")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val CAST_KEY = stringPreferencesKey("cast_key")
        val RECORDING_DIR = stringPreferencesKey("recording_dir")
        val CAST_PEERS = stringSetPreferencesKey("cast_peers")
        val REMINDERS = stringSetPreferencesKey("reminders")
        const val PEER_SEPARATOR = "\n"
        val PIN_RECORD = stringPreferencesKey("pin_record")
        val LOCKED_CATEGORIES = stringSetPreferencesKey("locked_categories")
        val LOCK_SECTIONS = listOf("live", "movies", "series")
        val HIDDEN_VOD_CATEGORIES = stringSetPreferencesKey("hidden_vod_categories")
        const val LIST_SEPARATOR = "\n"
        val TV_FAVORITE_HINT_SHOWN = booleanPreferencesKey("tv_favorite_hint_shown")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val UI_SCALE = stringPreferencesKey("ui_scale")
        val SCREENSAVER_MINUTES = intPreferencesKey("screensaver_minutes")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val ACTIVE_PLAYLIST_ID = longPreferencesKey("active_playlist_id")
        val VOD_SORT_MOVIES = stringPreferencesKey("vod_sort_movies")
        val VOD_SORT_SERIES = stringPreferencesKey("vod_sort_series")

        val BOOLEAN_KEYS: List<Preferences.Key<*>> = listOf(
            SUBTITLE_BACKGROUND, RESUME_LAST_CHANNEL, TV_CHANNEL_PREVIEW, TV_VOD_PREVIEW, DRAW_UNDER_CUTOUT, APP_FULL_SCREEN,
            PLAYER_BRIGHTNESS_SWIPE, PLAYER_VOLUME_SWIPE, BACKGROUND_PLAY, AUTOSTART, REOPEN_AFTER_UPDATE,
            AUTO_REFRESH_PLAYLISTS, VOD_LOCAL_CACHE, MATCH_FRAME_RATE, WATCH_NEXT, BLACK_THEME, TV_RAIL_ICONS_ONLY,
            PAUSE_INFO, DOWNLOAD_ON_MOBILE, TV_FAVORITE_HINT_SHOWN, ROTATE_HINT_SHOWN, AUTO_NEXT_EPISODE,
            RULE_HIDE_SD, RULE_HIDE_PLUS_ONE, RULE_MERGE_SAME_NAME, DATA_SAVER,
        )
        val INT_KEYS: List<Preferences.Key<*>> = listOf(PLAYER_SEEK_STEP, SCREENSAVER_MINUTES)
        val SET_KEYS: List<Preferences.Key<*>> = listOf(CAST_PEERS)

        /**
         * Podešavanja koja idu u izvoz i važe na svakom uređaju. Liste, zaključavanja, uređenje
         * kategorija i podsetnici nisu ovde: oni zavise od liste i putuju uz nju.
         */
        val GENERAL_KEYS: List<Preferences.Key<*>> = BOOLEAN_KEYS + INT_KEYS + listOf(
            PLAYER_BUFFER, SUBTITLE_SIZE, SUBTITLE_POSITION, PLAYER_LOUDNESS, PLAYER_AUDIO_LANGUAGE, PLAYER_SUBTITLE_LANGUAGE, APP_THEME,
            RECENT_SEARCHES, ACCENT_COLOR, UPDATE_CHANNEL, VOD_SORT_MOVIES, VOD_SORT_SERIES, WEATHER_CITY,
        )

        /** Podešavanja koja imaju smisla samo na istoj vrsti uređaja (telefon ili TV). */
        val DEVICE_KEYS: List<Preferences.Key<*>> = listOf(
            PLAYER_DECODER, UI_SCALE, RECORDING_DIR, CAST_KEY, CAST_PEERS, SCREEN_POSITION, CLASSIC_LOOK,
        )
    }
}
