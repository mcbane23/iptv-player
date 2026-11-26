package com.bane23.iptvplayer.data.repo

import androidx.room.withTransaction
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.VodFavoriteEntity
import com.bane23.iptvplayer.data.db.WatchProgressEntity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.trakt.TraktKey
import com.bane23.iptvplayer.data.trakt.TraktPayloads
import com.bane23.iptvplayer.data.trakt.TraktQueue
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.data.xtream.XtreamApi
import com.bane23.iptvplayer.data.xtream.XtreamJson
import com.bane23.iptvplayer.data.xtream.XtreamSeries
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.data.xtream.XtreamVodStream
import com.bane23.iptvplayer.domain.CategoryArrange
import com.bane23.iptvplayer.domain.ChannelRules
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.domain.StreamSource
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl

enum class VodKind { MOVIES, SERIES }

data class VodCategory(val id: String, val name: String)

/** Sakrivene kategorije (ID-jevi) i redosled koji je zadao korisnik. */
data class VodCategoryPrefs(val hidden: Set<String> = emptySet(), val order: List<String> = emptyList())

/** Film ili serija u spisku. */
data class VodItem(
    val id: Long,
    val name: String,
    val poster: String?,
    val rating: String?,
    /** Nastavak fajla filma; za serije null. */
    val extension: String?,
    /** ID na TMDB-u (za Trakt), ako ga panel šalje. */
    val tmdbId: Long? = null,
    /** Kada je dodat na listu (sekunde od 1970); 0 kad panel to ne šalje. */
    val added: Long = 0,
    /** Godina izlaska; 0 kad panel to ne šalje. */
    val year: Int = 0,
    /** Kategorija kojoj pripada, ako je panel šalje u spisku. */
    val categoryId: String? = null,
)

/**
 * Zapamćena pozicija gledanja filma ili epizode. [watched] je oznaka „odgledano”: postavi se sama
 * kad se pogleda do kraja ([FINISHED_PERCENT]) ili je korisnik postavi ručno.
 */
data class WatchProgress(
    val itemId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean = false,
    /** Kada je pozicija zapamćena (ms); 0 kad se ne zna ili kad je samo ručno označeno. */
    val updatedAt: Long = 0,
) {
    /** Odgledano: kvačica i, kod serija, nastavak od sledeće epizode. */
    val finished: Boolean get() = watched

    /** Vredi ponuditi nastavak: gledanje je počelo, a nije stiglo do kraja (i kad se odgledano gleda ponovo). */
    val resumable: Boolean get() = positionMs >= MIN_RESUME_MS && !reachedEnd(positionMs, durationMs)

    companion object {
        const val FINISHED_PERCENT = 95
        const val MIN_RESUME_MS = 30_000L

        /** Da li je pozicija stigla do kraja (95%), posle čega se film ili epizoda smatra odgledanim. */
        fun reachedEnd(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs * 100 >= durationMs * FINISHED_PERCENT

        /**
         * Da li pozicija sa Trakt naloga, zabeležena u [pausedAtMs], sme da se upiše za film ili seriju
         * koju je korisnik u [dismissedAtMs] sklonio iz reda „Nastavi gledanje” (null: nije sklonjeno).
         * Sme samo ako je nastala posle sklanjanja, to jest ako je gledano ponovo na drugom uređaju;
         * inače bi sinhronizacija vratila ono što je korisnik upravo sklonio.
         */
        fun remoteAllowed(dismissedAtMs: Long?, pausedAtMs: Long): Boolean =
            dismissedAtMs == null || pausedAtMs > dismissedAtMs
    }
}

/** Film ili epizoda koja se ručno označava kao odgledana. [title] i [poster] su filma, odnosno serije. */
data class WatchTarget(val itemId: Long, val title: String?, val poster: String?, val extension: String? = null)

/**
 * Stavka reda „Nastavi gledanje”: film ([seriesId] null) ili serija sa poslednje gledanom epizodom.
 * [id] je ID filma, odnosno serije; [progress] je deo koji je odgledan (0 do 1).
 */
data class ContinueItem(
    val id: Long,
    val name: String,
    val poster: String?,
    val extension: String?,
    val progress: Float,
    val isSeries: Boolean,
    /** Kada je poslednji put gledano (ms), za zajednički red filmova i serija na „Početnoj”. */
    val updatedAt: Long = 0,
    /** Zapamćena pozicija i trajanje (ms), za red na početnom ekranu TV-a. */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Sve jezičke verzije iste stavke koje su bile u redu (uključujući [id]); sklanjaju se zajedno. */
    val versionIds: List<Long> = listOf(id),
)

/**
 * Najskorije dodati filmovi, odnosno serije, najnoviji prvi, bez onih iz kategorija [hidden].
 * Stavke bez vremena dodavanja se preskaču: kad ih panel ne šalje, spisak je prazan.
 */
internal fun recentlyAdded(items: List<VodItem>, hidden: Set<String>, limit: Int): List<VodItem> =
    items.asSequence()
        .filter { it.added > 0 && (it.categoryId == null || it.categoryId !in hidden) }
        .sortedByDescending { it.added }
        .distinctBy { it.tmdbId ?: -it.id }
        .take(limit)
        .toList()

/**
 * Otisak onoga od čega zavisi sačuvan red „nedavno dodato”: sakrivene i zaključane kategorije i
 * dužina reda. Isti je posle ponovnog pokretanja aplikacije (ne zavisi od redosleda u skupu).
 */
internal fun addedSignature(hidden: Set<String>, locked: Set<String>, limit: Int): Long =
    listOf(hidden.sorted(), locked.sorted(), limit).hashCode().toLong()

/** Filmovi i serije iz „Nastavi gledanje” u jednom redu, poslednje gledano prvo. */
internal fun mergeContinue(movies: List<ContinueItem>, series: List<ContinueItem>, limit: Int): List<ContinueItem> =
    (movies + series).sortedByDescending { it.updatedAt }.take(limit)

/**
 * Koliko je verzija serije „engleska”: 0 za EN, 1 za UK/GB, 2 za US, 3 za AMZ, null kad nije engleska. Gleda se
 * oznaka ispred naziva („NL - …”), a kad je nema, oznaka ispred naziva kategorije ili reč „English” u njemu.
 */
internal fun englishRank(name: String, category: String?): Int? {
    val tag = ChannelRules.prefixTag(name.withoutQuality()) ?: ChannelRules.prefixTag(category?.withoutQuality())
    if (tag != null) return ENGLISH_TAGS[tag.uppercase()]
    return if (category != null && ENGLISH_WORD.containsMatchIn(category)) 0 else null
}

/** AMZ: Amazon, čije su serije na engleskom. */
private val ENGLISH_TAGS = mapOf("EN" to 0, "ENG" to 0, "UK" to 1, "GB" to 1, "GBR" to 1, "US" to 2, "USA" to 2, "AMZ" to 3)

/** Oznaka kvaliteta ispred oznake jezika („4K-AMZ - …”). */
private val QUALITY_LEAD = Regex("^[\\s\\[(]*(?:4K|UHD|FHD)[\\s-]+(?=[A-Z]{2,3}\\s*(?:[:|\\])]|\\s[-–]\\s))")

private fun String.withoutQuality(): String = QUALITY_LEAD.replace(this, "")
private val ENGLISH_WORD = Regex("(?<![\\p{L}])ENGLISH(?![\\p{L}])", RegexOption.IGNORE_CASE)

/**
 * Od više verzija iste serije (isti TMDB ID) bira koje idu u omiljene: one gledane u aplikaciji
 * ([watchedSeriesIds]), a ako takvih nema, jednu, englesku ako postoji (redom EN, UK/GB, US, AMZ), inače
 * prvu iz spiska. [categoryNames]: naziv kategorije po ID-ju.
 */
internal fun preferredVersions(
    series: List<VodItem>,
    watchedSeriesIds: Set<Long>,
    categoryNames: Map<String, String> = emptyMap(),
): List<VodItem> = series.groupBy { it.tmdbId }.values.flatMap { versions ->
    versions.filter { it.id in watchedSeriesIds }.ifEmpty {
        listOf(versions.minBy { englishRank(it.name, it.categoryId?.let(categoryNames::get)) ?: Int.MAX_VALUE })
    }
}

/**
 * Red „Nastavi gledanje” ([rows], poslednje gledano prvo) sa po jednom verzijom filma, odnosno serije: verzije
 * sa istim TMDB ID-jem ([catalog]) se spajaju u jednu. Ostaje verzija puštana na ovom uređaju ([playedHere]),
 * a ako takve nema, engleska (EN, UK/GB, US, AMZ), inače poslednje gledana. Stavka zadržava mesto i vreme
 * najskorije gledane verzije. [categoryNames]: naziv kategorije po ID-ju.
 */
internal fun oneVersionEach(
    rows: List<ContinueItem>,
    catalog: Map<Long, VodItem>,
    playedHere: Set<Long>,
    categoryNames: Map<String, String> = emptyMap(),
): List<ContinueItem> {
    val groups = LinkedHashMap<Long, MutableList<ContinueItem>>()
    for (row in rows) {
        val key = catalog[row.id]?.tmdbId?.takeIf { it > 0 } ?: (-row.id - 1)
        groups.getOrPut(key) { ArrayList() }.add(row)
    }
    return groups.values.map { versions ->
        if (versions.size == 1) return@map versions.single()
        val chosen = versions.firstOrNull { it.id in playedHere } ?: versions.minBy { row ->
            val item = catalog[row.id]
            englishRank(item?.name ?: row.name, item?.categoryId?.let(categoryNames::get)) ?: Int.MAX_VALUE
        }
        chosen.copy(updatedAt = versions.maxOf { it.updatedAt }, versionIds = versions.map { it.id })
    }
}

/**
 * Spisak filmova, odnosno serija, iz odgovora panela. Čita se stavka po stavka, bez učitavanja celog
 * odgovora u memoriju: ceo spisak velikog provajdera inače ne staje u memoriju TV uređaja.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun readVodItems(kind: VodKind, input: InputStream): List<VodItem> {
    val items = ArrayList<VodItem>()
    // Isti ID kategorije se ponavlja hiljadama puta; čuva se jedan primerak.
    val categoryIds = HashMap<String, String>()
    fun category(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let { categoryIds.getOrPut(it) { it } }
    // Neki paneli isti film ili seriju pošalju dvaput; ostaje prvi, jer ekran sa posterima traži da
    // svaki ima svoj ID.
    val seen = HashSet<Long>()
    if (kind == VodKind.MOVIES) {
        for (stream in XtreamJson.decodeToSequence(input, XtreamVodStream.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)) {
            val id = stream.streamId?.toLongOrNull() ?: continue
            if (!seen.add(id)) continue
            items.add(
                VodItem(
                    id = id,
                    name = stream.name?.takeIf { it.isNotBlank() } ?: id.toString(),
                    poster = stream.icon?.takeIf { it.isNotBlank() },
                    rating = stream.rating.cleanRating(),
                    extension = stream.extension?.takeIf { it.isNotBlank() },
                    tmdbId = XtreamVodParser.tmdbId(stream.tmdb) ?: XtreamVodParser.tmdbId(stream.tmdbId),
                    added = stream.added?.trim()?.toLongOrNull() ?: 0,
                    year = yearOf(stream.year, stream.releaseDate),
                    categoryId = category(stream.categoryId),
                ),
            )
        }
    } else {
        for (series in XtreamJson.decodeToSequence(input, XtreamSeries.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)) {
            val id = series.seriesId?.toLongOrNull() ?: continue
            if (!seen.add(id)) continue
            items.add(
                VodItem(
                    id = id,
                    name = series.name?.takeIf { it.isNotBlank() } ?: id.toString(),
                    poster = series.cover?.takeIf { it.isNotBlank() },
                    rating = series.rating.cleanRating(),
                    extension = null,
                    tmdbId = XtreamVodParser.tmdbId(series.tmdb) ?: XtreamVodParser.tmdbId(series.tmdbId),
                    added = series.lastModified?.trim()?.toLongOrNull() ?: 0,
                    year = yearOf(series.year, series.releaseDate, series.releaseDateAlt),
                    categoryId = category(series.categoryId),
                ),
            )
        }
    }
    items.trimToSize()
    return items
}

/** Godina iz prvog polja koje počinje sa četiri cifre (godina ili datum „2021-05-01”); inače 0. */
private fun yearOf(vararg values: String?): Int {
    for (value in values) {
        val year = value?.trim()?.take(4)?.toIntOrNull() ?: continue
        if (year in 1900..2100) return year
    }
    return 0
}

/** Ocena kao kratak tekst; "0" i prazno znače da ocene nema. */
private fun String?.cleanRating(): String? {
    val value = this?.trim()?.toDoubleOrNull() ?: return null
    return if (value <= 0.0) null else ((value * 10).toInt() / 10.0).toString()
}

/** Lista nije Xtream nalog, pa nema filmove i serije. */
class VodUnavailableException : IOException("Playlist has no movies or series")

/**
 * Filmovi i serije Xtream naloga. Spiskovi se ne čuvaju u bazi nego se preuzimaju po kategoriji
 * kad zatrebaju i pamte dok aplikacija radi; u bazi je samo dokle je korisnik stigao sa gledanjem.
 */
@Singleton
class VodRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val api: XtreamApi,
    private val cipher: PasswordCipher,
    private val locks: LockRepository,
    private val trakt: TraktQueue,
    private val disk: VodDiskCache,
) {
    private val playlistDao = db.playlistDao()
    private val progressDao = db.watchProgressDao()
    private val favoriteDao = db.vodFavoriteDao()

    private class Session(
        val playlistId: Long,
        val server: HttpUrl,
        val username: String,
        val password: String,
        val userAgent: String?,
        /** Početak ključeva keša za ovu listu, vidi [cachePrefix]. */
        val cache: String,
        /** Vreme poslednjeg osvežavanja liste (0 ako nije poznato); uz spiskove sačuvane na uređaju. */
        val stamp: Long,
    )

    private val cacheStamps = ConcurrentHashMap<Long, String>()

    /**
     * Početak ključeva keša: ID liste i vreme njenog poslednjeg osvežavanja. Kad se lista osveži
     * (ručno ili dnevnim osvežavanjem), spiskovi filmova i serija se zato preuzimaju iznova; bez toga
     * bi novi filmovi i epizode stigli tek kad se aplikacija ugasi i ponovo pokrene. Stari spiskovi
     * te liste se tada izbacuju iz memorije.
     */
    private fun cachePrefix(playlistId: Long, refreshedAt: Long?): String {
        val prefix = "$playlistId@${refreshedAt ?: 0}/"
        val previous = cacheStamps.put(playlistId, prefix)
        if (previous != null && previous != prefix) {
            categoryCache.keys.removeAll { it.startsWith(previous) }
            itemCache.keys.removeAll { it.startsWith(previous) }
            seriesCache.keys.removeAll { it.startsWith(previous) }
        }
        return prefix
    }

    private val categoryCache = ConcurrentHashMap<String, List<VodCategory>>()
    private val itemCache = ConcurrentHashMap<String, List<VodItem>>()
    private val seriesCache = ConcurrentHashMap<String, SeriesDetails>()

    private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _updates = MutableSharedFlow<VodKind>(extraBufferCapacity = 8)

    /**
     * Javlja kad je u pozadini stigao noviji spisak filmova, odnosno serija, od onog sačuvanog na
     * uređaju, da ekran može tiho da ga prikaže.
     */
    val updates: SharedFlow<VodKind> = _updates.asSharedFlow()

    /** Menja se kad se nešto zaključa ili otključa: spiskovi na ekranu tada treba da se slože iznova. */
    val lockChanges: Flow<Set<String>> get() = locks.activeKeys

    /** Da li se spiskovi filmova i serija čuvaju na uređaju (Podešavanja). */
    val localCacheEnabled: Flow<Boolean> get() = settings.vodLocalCache

    /** Upis pozicije mora da se završi i kad se ekran plejera zatvori. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Kategorije za prikaz: bez sakrivenih, redom koji je zadao korisnik. Kategorija koja je sada
     * zaključana ima katanac ispred naziva.
     */
    suspend fun categories(kind: VodKind): List<VodCategory> {
        val all = allCategories(kind)
        val playlistId = settings.activePlaylistId.first() ?: return all
        val prefs = observeCategoryPrefs(playlistId, kind).first()
        val visible = CategoryArrange.ordered(all.filter { it.id !in prefs.hidden }, prefs.order) { it.id }
        val keys = locks.activeKeysNow()
        if (keys.isEmpty()) return visible
        return visible.map { category ->
            val locked = LockRepository.vodKey(playlistId, kind == VodKind.SERIES, category.id) in keys
            if (locked) category.copy(name = LockRepository.LOCK_MARK + category.name) else category
        }
    }

    /** Šta je korisnik sakrio i kako je poređao kategorije filmova, odnosno serija, liste [playlistId]. */
    fun observeCategoryPrefs(playlistId: Long, kind: VodKind): Flow<VodCategoryPrefs> {
        val series = kind == VodKind.SERIES
        val prefix = LockRepository.vodKey(playlistId, series, "")
        return combine(settings.hiddenVodCategories, settings.vodCategoryOrder(playlistId, series)) { hidden, order ->
            VodCategoryPrefs(
                hidden = hidden.filter { it.startsWith(prefix) }.mapTo(HashSet()) { it.removePrefix(prefix) },
                order = order,
            )
        }.distinctUntilChanged()
    }

    /** Isto, za aktivnu listu (prati i promenu liste). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveCategoryPrefs(kind: VodKind): Flow<VodCategoryPrefs> =
        settings.activePlaylistId.flatMapLatest { playlistId ->
            if (playlistId == null) flowOf(VodCategoryPrefs()) else observeCategoryPrefs(playlistId, kind)
        }

    /** Sakriva ili vraća kategorije (ID-jevi sa servera) aktivne liste. */
    suspend fun setCategoriesHidden(kind: VodKind, categoryIds: Collection<String>, hidden: Boolean) {
        val playlistId = settings.activePlaylistId.first() ?: return
        val series = kind == VodKind.SERIES
        settings.setVodCategoriesHidden(categoryIds.map { LockRepository.vodKey(playlistId, series, it) }, hidden)
    }

    /** Pamti redosled kategorija aktivne liste; [categoryIds] su sve kategorije, redom prikaza. */
    suspend fun setCategoryOrder(kind: VodKind, categoryIds: List<String>) {
        val playlistId = settings.activePlaylistId.first() ?: return
        settings.setVodCategoryOrder(playlistId, kind == VodKind.SERIES, categoryIds)
    }

    /** Prebacuje kategoriju na vrh spiska. */
    suspend fun moveCategoryToTop(kind: VodKind, categoryId: String) {
        val playlistId = settings.activePlaylistId.first() ?: return
        val prefs = observeCategoryPrefs(playlistId, kind).first()
        val ids = CategoryArrange.ordered(allCategories(kind), prefs.order) { it.id }.map { it.id }
        val index = ids.indexOf(categoryId)
        if (index > 0) setCategoryOrder(kind, CategoryArrange.movedToTop(ids, index))
    }

    /** Šta je u sekciji bilo otvoreno poslednji put (ID kategorije ili posebna oznaka), ili null. */
    suspend fun lastCategory(kind: VodKind): String? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return settings.lastCategory(playlistId, kind.section).first()
    }

    suspend fun setLastCategory(kind: VodKind, value: String) {
        val playlistId = settings.activePlaylistId.first() ?: return
        settings.setLastCategory(playlistId, kind.section, value)
    }

    /** Zapamćen redosled filmova, odnosno serija (isti za sve liste). */
    suspend fun sort(kind: VodKind): VodSort = VodSort.of(settings.vodSort(kind == VodKind.SERIES).first())

    /** Zapamćen filter filmova, odnosno serija (isti za sve liste). */
    suspend fun filter(kind: VodKind): VodFilter = VodFilter.decode(settings.vodFilter(kind == VodKind.SERIES).first())

    suspend fun setFilter(kind: VodKind, filter: VodFilter) = settings.setVodFilter(kind == VodKind.SERIES, filter.encode())

    suspend fun setSort(kind: VodKind, sort: VodSort) = settings.setVodSort(kind == VodKind.SERIES, sort.name)

    private val VodKind.section get() = if (this == VodKind.SERIES) "series" else "movies"

    /** Kategorije onako kako ih šalje server, za ekran roditeljske zaštite. */
    suspend fun allCategories(kind: VodKind): List<VodCategory> = withContext(Dispatchers.IO) {
        val session = session()
        cached(
            memory = categoryCache,
            key = "${session.cache}$kind",
            kind = kind,
            readDisk = { disk.readCategories(session.playlistId, kind) },
            writeDisk = { disk.writeCategories(session.playlistId, kind, session.stamp, it) },
            stamp = session.stamp,
        ) {
            val action = if (kind == VodKind.MOVIES) XtreamUrls.ACTION_VOD_CATEGORIES else XtreamUrls.ACTION_SERIES_CATEGORIES
            api.categories(session.api(action), session.userAgent).mapNotNull { category ->
                val id = category.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VodCategory(id, category.name?.takeIf { it.isNotBlank() } ?: id)
            }.distinctBy { it.id }
        }
    }

    /**
     * Spisak kategorije bez sadržaja zaključanih kategorija (roditeljska zaštita). Zaključana
     * kategorija je prazna, a iz spiska „sve” nestaju filmovi i serije koji joj pripadaju.
     */
    suspend fun items(kind: VodKind, categoryId: String): List<VodItem> {
        val hidden = lockedIds(kind)
        val all = loadItems(kind, categoryId)
        return if (hidden.isEmpty()) all else all.filter { it.id !in hidden }
    }

    /** Da li je film, odnosno serija, sada zaključan: pojedinačno ili kroz svoju kategoriju. */
    suspend fun isLocked(kind: VodKind, itemId: Long): Boolean = itemId in lockedIds(kind)

    /** ID-jevi filmova, odnosno serija, koji su sada zaključani pojedinačno ili kroz kategoriju. */
    private suspend fun lockedIds(kind: VodKind): Set<Long> {
        val keys = locks.activeKeysNow()
        if (keys.isEmpty()) return emptySet()
        val playlistId = settings.activePlaylistId.first() ?: return emptySet()
        val series = kind == VodKind.SERIES
        val prefix = LockRepository.vodKey(playlistId, series, "")
        val categoryIds = keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        val ids = HashSet<Long>(LockRepository.vodItemIds(keys, playlistId, series))
        for (categoryId in categoryIds) loadItems(kind, categoryId).mapTo(ids) { it.id }
        return ids
    }

    /** Isti spisak, ali bez stavki iz kategorija koje su sada zaključane. */
    private fun <T> Flow<List<T>>.withoutLocked(kind: VodKind, id: (T) -> Long): Flow<List<T>> =
        combine(this, locks.activeKeys) { list, keys -> list to keys }.map { (list, keys) ->
            if (keys.isEmpty()) return@map list
            val hidden = try {
                lockedIds(kind)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bez spiska sa servera ne zna se šta je zaključano, pa se radije ne prikazuje ništa.
                return@map emptyList()
            }
            list.filter { id(it) !in hidden }
        }

    private suspend fun loadItems(kind: VodKind, categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val session = session()
        cached(
            memory = itemCache,
            key = "${session.cache}$kind/$categoryId",
            kind = kind,
            readDisk = { disk.readItems(session.playlistId, kind, categoryId) },
            writeDisk = { disk.writeItems(session.playlistId, kind, categoryId, session.stamp, it) },
            stamp = session.stamp,
        ) {
            val url = session.listUrl(if (kind == VodKind.MOVIES) XtreamUrls.ACTION_VOD_STREAMS else XtreamUrls.ACTION_SERIES, categoryId)
            val body = if (kind == VodKind.MOVIES) api.vodStreams(url, session.userAgent) else api.series(url, session.userAgent)
            body.use { readVodItems(kind, it.byteStream()) }
        }
    }

    suspend fun movie(movieId: Long): MovieDetails = withContext(Dispatchers.IO) {
        val session = session()
        val json = api.details(session.api(XtreamUrls.ACTION_VOD_INFO, "vod_id", movieId.toString()), session.userAgent)
        XtreamVodParser.movie(json)
    }

    suspend fun series(seriesId: Long): SeriesDetails = withContext(Dispatchers.IO) {
        val session = session()
        seriesCache.getOrPutSuspending("${session.cache}$seriesId") {
            val url = session.api(XtreamUrls.ACTION_SERIES_INFO, "series_id", seriesId.toString())
            XtreamVodParser.series(api.details(url, session.userAgent))
        }
    }

    suspend fun movieSource(movieId: Long, extension: String): StreamSource = withContext(Dispatchers.IO) {
        val session = session()
        val url = XtreamUrls.movieStream(session.server, session.username, session.password, movieId, extension)
        StreamSource(listOf(url), session.userAgent, referrer = null)
    }

    suspend fun episodeSource(episodeId: Long, extension: String): StreamSource = withContext(Dispatchers.IO) {
        val session = session()
        val url = XtreamUrls.episodeStream(session.server, session.username, session.password, episodeId, extension)
        StreamSource(listOf(url), session.userAgent, referrer = null)
    }

    /**
     * Adresa fajla za preuzimanje na uređaj. Za razliku od puštanja, lista je zadata: preuzimanje
     * može da čeka u redu i dok je aktivna neka druga lista.
     */
    suspend fun downloadSource(playlistId: Long, episode: Boolean, itemId: Long, extension: String): StreamSource =
        withContext(Dispatchers.IO) {
            val session = session(playlistId)
            val url = if (episode) {
                XtreamUrls.episodeStream(session.server, session.username, session.password, itemId, extension)
            } else {
                XtreamUrls.movieStream(session.server, session.username, session.password, itemId, extension)
            }
            StreamSource(listOf(url), session.userAgent, referrer = null)
        }

    suspend fun movieProgress(movieId: Long): WatchProgress? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return progressDao.get(playlistId, KIND_MOVIE, movieId)?.toDomain()
    }

    fun observeMovieProgress(playlistId: Long, movieId: Long): Flow<WatchProgress?> =
        progressDao.observe(playlistId, KIND_MOVIE, movieId).map { it?.toDomain() }

    suspend fun episodeProgress(episodeId: Long): WatchProgress? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return progressDao.get(playlistId, KIND_EPISODE, episodeId)?.toDomain()
    }

    /** Napredak epizoda serije, poslednje gledana prva. */
    fun observeSeriesProgress(playlistId: Long, seriesId: Long): Flow<List<WatchProgress>> =
        progressDao.observeSeries(playlistId, seriesId).map { rows -> rows.map { it.toDomain() } }

    val activePlaylistId: Flow<Long?> = settings.activePlaylistId

    /** Filmovi, odnosno serije, koje je korisnik počeo da gleda; poslednje gledano prvo. */
    fun observeContinue(playlistId: Long, kind: VodKind): Flow<List<ContinueItem>> =
        observeContinueOrWaiting(playlistId, kind).filterNotNull()

    /**
     * Kao [observeContinue], ali null dok se jezičke verzije spajaju čitanjem celog spiska liste (posle
     * pokretanja to potraje i nekoliko sekundi), da ekran ne prikaže na tren sve verzije (Bane).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeContinueOrWaiting(playlistId: Long, kind: VodKind): Flow<List<ContinueItem>?> {
        // Više redova nego što se prikazuje: jezičke verzije istog filma se posle spajaju u jedan.
        val rows = if (kind == VodKind.MOVIES) {
            progressDao.observeContinueMovies(
                playlistId,
                WatchProgress.MIN_RESUME_MS,
                WatchProgress.FINISHED_PERCENT,
                CONTINUE_ROWS,
            )
        } else {
            progressDao.observeContinueSeries(playlistId, CONTINUE_ROWS)
        }
        return rows.withoutLocked(kind) { if (kind == VodKind.SERIES) it.seriesId else it.itemId }.map { list ->
            list.map { row ->
                val isSeries = kind == VodKind.SERIES
                ContinueItem(
                    id = if (isSeries) row.seriesId else row.itemId,
                    name = row.title.orEmpty(),
                    poster = row.poster,
                    extension = row.extension,
                    progress = if (row.durationMs > 0) (row.positionMs.toFloat() / row.durationMs).coerceIn(0f, 1f) else 0f,
                    isSeries = isSeries,
                    updatedAt = row.updatedAt,
                    positionMs = row.positionMs,
                    durationMs = row.durationMs,
                )
            }
        }.transformLatest { list ->
            if (list.size >= 2 && !versionsKnown(playlistId, kind, list)) emit(null)
            emit(oneVersionEach(playlistId, kind, list).take(CONTINUE_LIMIT))
        }.distinctUntilChanged()
    }

    /**
     * Da li se verzije stavki [rows] spajaju odmah: ceo spisak liste je već u memoriji, ili su sve stavke
     * među sačuvanim pri prošlom spajanju.
     */
    private suspend fun versionsKnown(playlistId: Long, kind: VodKind, rows: List<ContinueItem>): Boolean =
        catalogInMemory(kind) || storedVersions(playlistId, kind, rows.mapTo(HashSet()) { it.id }) != null

    /** Sačuvane stavke reda „Nastavi gledanje” (vidi [VodDiskCache.readVersions]); null kad neka od [ids] fali. */
    private suspend fun storedVersions(playlistId: Long, kind: VodKind, ids: Set<Long>): Map<Long, VodItem>? =
        withContext(Dispatchers.IO) {
            if (!settings.vodLocalCache.first()) return@withContext null
            val stored = disk.readVersions(playlistId, kind)?.value ?: return@withContext null
            stored.filter { it.id in ids }.associateBy { it.id }.takeIf { it.size == ids.size }
        }

    /**
     * Stavke [ids] iz spiska liste, za spajanje verzija. Posle pokretanja se uzimaju sačuvane, ako su sve tu;
     * inače se čita ceo spisak i izdvojene stavke se sačuvaju za sledeći put. Stavka koje više nema na listi
     * se čuva bez TMDB ID-ja, da sledeći put ne traži ponovo ceo spisak.
     */
    private suspend fun versionCatalog(playlistId: Long, kind: VodKind, ids: Set<Long>): Map<Long, VodItem> {
        if (!catalogInMemory(kind)) storedVersions(playlistId, kind, ids)?.let { return it }
        val all = allItems(kind)
        val catalog = withContext(Dispatchers.Default) { all.filter { it.id in ids }.associateBy { it.id } }
        if (settings.vodLocalCache.first()) {
            val missing = ids.filter { it !in catalog }.map { VodItem(id = it, name = "", poster = null, rating = null, extension = null) }
            withContext(Dispatchers.IO) { disk.writeVersions(playlistId, kind, catalog.values + missing) }
        }
        return catalog
    }

    /** Da li je ceo spisak filmova, odnosno serija, aktivne liste već u memoriji (brzo spajanje verzija). */
    private suspend fun catalogInMemory(kind: VodKind): Boolean = try {
        itemCache.containsKey("${session().cache}$kind/$ALL_CATEGORIES")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /**
     * Spaja jezičke verzije u redu „Nastavi gledanje”, vidi [oneVersionEach]. Kad spisak sa servera nije
     * dostupan, red ostaje kakav jeste.
     */
    private suspend fun oneVersionEach(playlistId: Long, kind: VodKind, rows: List<ContinueItem>): List<ContinueItem> {
        if (rows.size < 2) return rows
        return try {
            val catalog = versionCatalog(playlistId, kind, rows.mapTo(HashSet()) { it.id })
            val tmdbIds = catalog.values.mapNotNull { it.tmdbId?.takeIf { id -> id > 0 } }
            if (tmdbIds.size == tmdbIds.toSet().size) return rows
            val playedHere = withContext(Dispatchers.IO) {
                if (kind == VodKind.MOVIES) progressDao.moviesPlayedHere(playlistId) else progressDao.seriesPlayedHere(playlistId)
            }.toSet()
            val categories = categoryNames(kind)
            withContext(Dispatchers.Default) { oneVersionEach(rows, catalog, playedHere, categories) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            rows
        }
    }

    /**
     * Sklanja film, odnosno seriju, iz reda „Nastavi gledanje”. Zapamćena pozicija i oznake
     * „odgledano” ostaju. Stavka se vraća u red tek kad se ponovo gleda, ovde ili na drugom uređaju
     * (Trakt), a pozicija koja je čekala slanje na Trakt se odbacuje.
     */
    suspend fun dismissContinue(item: ContinueItem) = withContext(Dispatchers.IO) {
        val playlistId = settings.activePlaylistId.first() ?: return@withContext
        val now = System.currentTimeMillis()
        // Sve jezičke verzije spojene u ovu stavku, da se posle sklanjanja ne pojavi druga.
        for (id in item.versionIds.ifEmpty { listOf(item.id) }) {
            if (item.isSeries) {
                progressDao.dismissSeries(playlistId, id, now)
                trakt.dropPlaybackOfSeries(playlistId, id)
            } else {
                progressDao.dismissMovie(playlistId, id, now)
                trakt.dropPlayback(playlistId, KIND_MOVIE, id)
            }
        }
    }

    /**
     * Najskorije dodati filmovi, odnosno serije, aktivne liste, bez sakrivenih i zaključanih
     * kategorija. Čita ceo spisak liste (isti koji koristi pretraga), pa prvi poziv može da potraje;
     * zato se ishod čuva na uređaju, vidi [storedRecentlyAdded].
     */
    suspend fun recentlyAdded(kind: VodKind, limit: Int): List<VodItem> {
        val playlistId = settings.activePlaylistId.first() ?: return emptyList()
        val hidden = observeCategoryPrefs(playlistId, kind).first().hidden
        val signature = addedSignature(hidden, locks.activeKeysNow(), limit)
        val all = allItems(kind)
        val result = withContext(Dispatchers.Default) { recentlyAdded(all, hidden, limit) }
        if (settings.vodLocalCache.first()) {
            withContext(Dispatchers.IO) { disk.writeAdded(playlistId, kind, signature, result) }
        }
        return result
    }

    /**
     * Red „nedavno dodato” sačuvan pri prošlom čitanju, da se „Početna” prikaže odmah po pokretanju.
     * Null kad ga nema, kad je čuvanje na uređaju isključeno ili kad su se u međuvremenu promenile
     * sakrivene, odnosno zaključane kategorije (da zaključan sadržaj ne bude prikazan ni na tren).
     */
    suspend fun storedRecentlyAdded(kind: VodKind, limit: Int): List<VodItem>? = withContext(Dispatchers.IO) {
        if (!settings.vodLocalCache.first()) return@withContext null
        val playlistId = settings.activePlaylistId.first() ?: return@withContext null
        val stored = disk.readAdded(playlistId, kind) ?: return@withContext null
        val hidden = observeCategoryPrefs(playlistId, kind).first().hidden
        stored.value.takeIf { stored.stamp == addedSignature(hidden, locks.activeKeysNow(), limit) }
    }

    /**
     * Svi filmovi, odnosno sve serije liste, za pretragu kroz sve kategorije.
     * Učitava se jednim zahtevom (bez kategorije) i čuva u memoriji kao i spiskovi kategorija.
     */
    suspend fun allItems(kind: VodKind): List<VodItem> = items(kind, ALL_CATEGORIES)

    /**
     * Pamti poziciju gledanja. [seriesId] je null za film. [title] i [poster] su naziv i poster filma,
     * odnosno serije, za red „Nastavi gledanje”. Ne čeka da se upis završi.
     */
    fun saveProgress(
        itemId: Long,
        seriesId: Long?,
        positionMs: Long,
        durationMs: Long,
        title: String? = null,
        poster: String? = null,
        extension: String? = null,
        stopped: Boolean = false,
    ) {
        if (durationMs <= 0) return
        writeScope.launch {
            val playlistId = settings.activePlaylistId.first() ?: return@launch
            val kind = if (seriesId == null) KIND_MOVIE else KIND_EPISODE
            val newlyWatched = db.withTransaction {
                val now = System.currentTimeMillis()
                // Oznaka „odgledano” ostaje i kad se film gleda ponovo; nova se dobija gledanjem do kraja.
                val previous = progressDao.get(playlistId, kind, itemId)?.watchedAt
                val watchedAt = previous ?: now.takeIf { WatchProgress.reachedEnd(positionMs, durationMs) }
                progressDao.upsert(
                    WatchProgressEntity(
                        playlistId = playlistId,
                        kind = kind,
                        itemId = itemId,
                        seriesId = seriesId ?: 0,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        updatedAt = now,
                        title = title,
                        poster = poster,
                        extension = extension,
                        watchedAt = watchedAt,
                        playedHereAt = now,
                    ),
                )
                previous == null && watchedAt != null
            }
            if (newlyWatched) trakt.changed(playlistId, kind, listOf(itemId), seriesId ?: 0, watched = true)
            // Prekinuto pre kraja: pozicija ide na Trakt, da se nastavi na drugom uređaju.
            if (stopped && positionMs >= WatchProgress.MIN_RESUME_MS && !WatchProgress.reachedEnd(positionMs, durationMs)) {
                val percent = (positionMs * 100f / durationMs).coerceIn(0f, 100f)
                trakt.playbackChanged(playlistId, kind, itemId, seriesId ?: 0, percent)
            }
        }
    }

    /**
     * Pozicija sa Trakt naloga (gledano na drugom uređaju): [progress] je procenat, [pausedAtMs]
     * vreme prekida. Upisuje se samo ako je novija od ovde zapamćene i stvarno drugačija.
     *
     * Ista serija ili film često postoji kod provajdera više puta (jezičke verzije, druge kategorije), sa
     * istim TMDB ID-jem. Pozicija ide na svaku verziju, da nastavak radi koju god da korisnik pusti. Verzija
     * sklonjena iz „Nastavi gledanje” se preskače (osim one koja se ovde gleda), pa jednom sklonjena ostaje
     * sklonjena.
     */
    suspend fun applyRemoteProgress(key: TraktKey, progress: Float, pausedAtMs: Long, fromOther: Boolean) = withContext(Dispatchers.IO) {
        val playlistId = session().playlistId
        val isMovie = key.kind == TraktKey.MOVIE
        val items = allItems(if (isMovie) VodKind.MOVIES else VodKind.SERIES)
        val recent = if (isMovie) progressDao.recentMovies(playlistId, REMOTE_MATCH_LIMIT) else progressDao.recentSeries(playlistId, REMOTE_MATCH_LIMIT)
        val listed = items.associateBy { it.id }
        // Isti redosled kao pri slanju: film prvo iz spiska, serija prvo iz detalja.
        val here = watchedHere(recent, key.tmdbId) { id ->
            if (isMovie) listed[id]?.tmdbId ?: movie(id).tmdbId else series(id).tmdbId ?: listed[id]?.tmdbId
        }
        val versions = (listOfNotNull(here) + items.filter { it.tmdbId == key.tmdbId }.map { it.id }).distinct()
        var applied = 0
        var failure: Exception? = null
        for (id in versions) {
            if (id != here && dismissedVersion(playlistId, isMovie, id)) continue
            try {
                val target = if (isMovie) remoteMovie(playlistId, id, items) else remoteEpisode(id, items, key)
                if (target != null && applyRemoteTo(playlistId, target, progress, pausedAtMs, fromOther)) applied++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Greška panela za jednu verziju ne sprečava ostale; ako nijedna ne uspe, pozicija čeka sledeću sinhronizaciju.
                if (failure == null) failure = e
            }
        }
        if (applied == 0) failure?.let { throw it }
    }

    private suspend fun dismissedVersion(playlistId: Long, isMovie: Boolean, id: Long): Boolean =
        if (isMovie) {
            progressDao.get(playlistId, KIND_MOVIE, id)?.dismissedAt != null
        } else {
            progressDao.seriesDismissedAt(playlistId, id) != null
        }

    private suspend fun remoteMovie(playlistId: Long, movieId: Long, items: List<VodItem>): RemoteTarget {
        val item = items.firstOrNull { it.id == movieId }
        val row = progressDao.get(playlistId, KIND_MOVIE, movieId)
        return RemoteTarget(movieId, null, item?.name ?: row?.title, item?.poster ?: row?.poster, item?.extension ?: row?.extension) {
            movie(movieId).durationSeconds
        }
    }

    private suspend fun remoteEpisode(showId: Long, items: List<VodItem>, key: TraktKey): RemoteTarget? {
        val show = items.firstOrNull { it.id == showId }
        val details = series(showId)
        val episode = details.episodes.firstOrNull { it.season == key.season && it.number == key.episode } ?: return null
        return RemoteTarget(episode.id, showId, details.name ?: show?.name, show?.poster ?: details.poster, null) {
            episode.durationSeconds
        }
    }

    private suspend fun applyRemoteTo(playlistId: Long, target: RemoteTarget, progress: Float, pausedAtMs: Long, fromOther: Boolean): Boolean {
        val kind = if (target.seriesId == null) KIND_MOVIE else KIND_EPISODE
        val existing = progressDao.get(playlistId, kind, target.itemId)
        // Trakt ne menja `paused_at` pri novoj pauzi iste epizode, pa vreme samo nije dovoljno: pozicija koju
        // ovaj uređaj nije poslao je novija od svega što je ovde već poslato. Ovde je novije samo ono što
        // još čeka slanje.
        val newerHere = existing != null && existing.updatedAt >= pausedAtMs &&
            (!fromOther || trakt.hasPendingPlayback(playlistId, kind, target.itemId))
        if (newerHere) return false
        // Ovde je posle te pauze odgledano do kraja ili označeno: stara pozicija se ne vraća.
        if (TraktPayloads.finishedSince(existing?.watchedAt, pausedAtMs)) return false
        // Sklonjeno iz reda „Nastavi gledanje” ne vraća pozicija starija od sklanjanja.
        val dismissedAt = target.seriesId?.let { progressDao.seriesDismissedAt(playlistId, it) } ?: existing?.dismissedAt
        if (!WatchProgress.remoteAllowed(dismissedAt, pausedAtMs)) return false
        val durationMs = existing?.durationMs?.takeIf { it > 0 } ?: target.durationSeconds()?.times(1000) ?: return false
        val positionMs = (durationMs * progress / 100).toLong()
        if (existing != null && kotlin.math.abs(existing.positionMs - positionMs) < SAME_POSITION_MS) return false
        progressDao.upsert(
            WatchProgressEntity(
                playlistId = playlistId,
                kind = kind,
                itemId = target.itemId,
                seriesId = target.seriesId ?: 0,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = pausedAtMs,
                title = target.title,
                poster = target.poster,
                extension = target.extension,
                watchedAt = existing?.watchedAt,
                playedHereAt = existing?.playedHereAt,
            ),
        )
        return true
    }

    /**
     * Verzija koja se ovde već gleda ([recent], od poslednje). TMDB ID se određuje isto kao kad se
     * pozicija šalje ([resolve]). Tako se nađe i verzija čiji spisak ima drugi ili
     * nikakav TMDB ID. Greška panela za jednu stavku ne prekida traženje.
     */
    private suspend fun watchedHere(
        recent: List<Long>,
        tmdbId: Long,
        resolve: suspend (Long) -> Long?,
    ): Long? {
        for (id in recent) {
            val resolved = try {
                resolve(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (resolved == tmdbId) return id
        }
        return null
    }

    private class RemoteTarget(
        val itemId: Long,
        val seriesId: Long?,
        val title: String?,
        val poster: String?,
        val extension: String?,
        val durationSeconds: suspend () -> Long?,
    )

    /**
     * Ručno označava filmove, odnosno epizode jedne serije ([seriesId] null za film), kao odgledane
     * ili poništava oznaku. Epizode idu redom kojim se gledaju, da bi nastavak serije krenuo posle
     * poslednje označene. [traktKeys] su isti filmovi, odnosno epizode, kako ih zna Trakt: kad se oznaka
     * poništava, odmah se skida i ona preuzeta sa Trakt-a.
     */
    suspend fun setWatched(
        seriesId: Long?,
        targets: List<WatchTarget>,
        watched: Boolean,
        traktKeys: List<TraktKey> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext
        val playlistId = settings.activePlaylistId.first() ?: return@withContext
        val kind = if (seriesId == null) KIND_MOVIE else KIND_EPISODE
        val ids = targets.map { it.itemId }
        db.withTransaction {
            val existing = progressDao.getMany(playlistId, kind, ids).associateBy { it.itemId }
            if (watched) {
                val now = System.currentTimeMillis()
                val rows = targets.mapIndexed { index, target ->
                    existing[target.itemId]?.let { row ->
                        // Već odgledano ostaje kako jeste. Započeto se računa kao pogledano do kraja,
                        // da ne bi nudilo nastavak i da bi serija krenula od sledeće epizode.
                        if (row.watchedAt != null) row else row.copy(watchedAt = now, positionMs = row.durationMs)
                    } ?: WatchProgressEntity(
                        playlistId = playlistId,
                        kind = kind,
                        itemId = target.itemId,
                        seriesId = seriesId ?: 0,
                        positionMs = 0,
                        durationMs = 0,
                        // Nikad gledano: vreme je redni broj, pa je ovo starije od svega stvarno gledanog,
                        // a među označenim epizodama poslednja je ona koja je poslednja i po redu.
                        updatedAt = index.toLong(),
                        // Bez naziva, da ručno označeno ne upadne u red „Nastavi gledanje”.
                        title = null,
                        poster = null,
                        extension = target.extension,
                        watchedAt = now,
                    )
                }
                progressDao.upsertAll(rows)
            } else {
                // Ono što nikad nije ni puštano briše se; inače se skida oznaka, a gledanje do kraja
                // vraća na početak, da film ne bi odmah ponovo bio odgledan.
                val (neverPlayed, played) = existing.values.partition { it.durationMs <= 0 }
                if (neverPlayed.isNotEmpty()) progressDao.deleteMany(playlistId, kind, neverPlayed.map { it.itemId })
                if (played.isNotEmpty()) progressDao.upsertAll(
                    played.map { row ->
                        val position = if (WatchProgress.reachedEnd(row.positionMs, row.durationMs)) 0 else row.positionMs
                        row.copy(watchedAt = null, positionMs = position)
                    },
                )
            }
        }
        if (!watched) trakt.forget(traktKeys)
        trakt.changed(playlistId, kind, ids, seriesId ?: 0, watched)
    }

    /** TMDB ID filma (za Trakt): iz već učitanih spiskova, inače iz detalja filma ili celog spiska. Null ako ga panel ne šalje. */
    suspend fun movieTmdbId(movieId: Long): Long? =
        cachedListTmdbId(VodKind.MOVIES, movieId) ?: movie(movieId).tmdbId ?: movieListTmdbId(movieId)

    /** TMDB ID filma iz spiskova (neki paneli ga šalju samo tamo). */
    suspend fun movieListTmdbId(movieId: Long): Long? = listTmdbId(VodKind.MOVIES, movieId)

    /** Epizoda kako je zna Trakt: TMDB ID serije, sezona i broj. Null ako panel ne šalje TMDB ID serije. */
    suspend fun episodeTraktKey(seriesId: Long, episodeId: Long): TraktKey? {
        val details = series(seriesId)
        val tmdb = details.tmdbId ?: seriesListTmdbId(seriesId) ?: return null
        val episode = details.episodes.firstOrNull { it.id == episodeId } ?: return null
        return TraktKey.episode(tmdb, episode.season, episode.number)
    }

    /** TMDB ID serije iz spiskova (neki paneli ga šalju samo tamo). */
    suspend fun seriesListTmdbId(seriesId: Long): Long? = listTmdbId(VodKind.SERIES, seriesId)

    /**
     * TMDB ID iz spiskova: prvo iz onih koji su već u memoriji, inače se učitava ceo spisak. Bez toga
     * serija otvorena pre nego što je ijedan spisak učitan (npr. iz reda „Nastavi gledanje”) ne bi
     * imala TMDB ID, pa ni oznake sa Trakt-a.
     */
    private suspend fun listTmdbId(kind: VodKind, itemId: Long): Long? =
        cachedListTmdbId(kind, itemId) ?: loadItems(kind, ALL_CATEGORIES).firstOrNull { it.id == itemId }?.tmdbId

    private suspend fun cachedListTmdbId(kind: VodKind, itemId: Long): Long? {
        val prefix = "${session().cache}$kind/"
        return itemCache.entries.asSequence()
            .filter { it.key.startsWith(prefix) }
            .firstNotNullOfOrNull { (_, list) -> list.firstOrNull { it.id == itemId }?.tmdbId }
    }

    /** ID-jevi odgledanih filmova aktivne liste, za kvačicu na posteru. */
    fun observeWatchedMovieIds(playlistId: Long): Flow<Set<Long>> =
        progressDao.observeWatchedIds(playlistId, KIND_MOVIE).map { it.toSet() }

    /** Omiljeni filmovi, odnosno serije, liste; poslednje dodat prvi. */
    fun observeFavorites(playlistId: Long, kind: VodKind): Flow<List<VodItem>> =
        favoriteDao.observe(playlistId, kind.favoriteKind).withoutLocked(kind) { it.itemId }.map { list ->
            list.map { VodItem(id = it.itemId, name = it.name, poster = it.poster, rating = null, extension = it.extension) }
        }

    fun observeIsFavorite(playlistId: Long, kind: VodKind, itemId: Long): Flow<Boolean> =
        favoriteDao.observeIsFavorite(playlistId, kind.favoriteKind, itemId)

    /** Dodaje film ili seriju u omiljene, odnosno uklanja ako je već tamo. */
    suspend fun toggleFavorite(kind: VodKind, item: VodItem, favorite: Boolean) = withContext(Dispatchers.IO) {
        val playlistId = settings.activePlaylistId.first() ?: return@withContext
        if (favorite) {
            favoriteDao.delete(playlistId, kind.favoriteKind, item.id)
        } else {
            favoriteDao.insert(
                VodFavoriteEntity(
                    playlistId = playlistId,
                    kind = kind.favoriteKind,
                    itemId = item.id,
                    name = item.name,
                    poster = item.poster,
                    extension = item.extension,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Dodaje u omiljene serije liste [playlistId] sa datim TMDB ID-jevima (započete na Trakt nalogu),
     * tako da poslednja u spisku bude prva u omiljenima. Kad provajder istu seriju ima u više verzija
     * (npr. po jezicima), dodaje se ona gledana u aplikaciji, a inače jedna, po [preferredVersions]. Vraća TMDB ID-jeve koji su nađeni u spisku
     * serija; ostalih kod provajdera nema (ili su u zaključanoj kategoriji).
     */
    suspend fun addSeriesFavorites(playlistId: Long, tmdbIds: List<Long>): Set<Long> = withContext(Dispatchers.IO) {
        if (session().playlistId != playlistId) return@withContext emptySet()
        val wanted = tmdbIds.withIndex().associate { (index, id) -> id to index }
        val matches = allItems(VodKind.SERIES).filter { it.tmdbId != null && wanted.containsKey(it.tmdbId) }.distinctBy { it.id }
        val found = preferredVersions(matches, progressDao.seriesWithProgress(playlistId).toSet(), categoryNames(VodKind.SERIES))
        val now = System.currentTimeMillis()
        favoriteDao.insertMissing(
            found.map { item ->
                VodFavoriteEntity(
                    playlistId = playlistId,
                    kind = KIND_SERIES,
                    itemId = item.id,
                    name = item.name,
                    poster = item.poster,
                    extension = null,
                    addedAt = now + (item.tmdbId?.let { wanted[it] } ?: 0),
                )
            },
        )
        found.mapNotNullTo(HashSet()) { it.tmdbId }
    }

    /**
     * Jednokratno čišćenje omiljenih liste [playlistId]: od serija koje je Trakt dodao ([autoTmdbIds]) u
     * više verzija ostaje samo jedna, ona koju bira [preferredVersions] (engleska, a bez nje prva iz
     * spiska). Serija čija je neka verzija gledana u aplikaciji, ili je u omiljenima samo jednom, ostaje
     * kakva jeste.
     * Vraća false kad lista nije aktivna, pa čišćenje treba ponoviti kasnije.
     */
    suspend fun trimAutoSeriesFavorites(playlistId: Long, autoTmdbIds: Set<Long>): Boolean = withContext(Dispatchers.IO) {
        if (autoTmdbIds.isEmpty()) return@withContext true
        if (session().playlistId != playlistId) return@withContext false
        val series = allItems(VodKind.SERIES)
        // Prazan spisak znači da server trenutno ne odgovara kako treba: čišćenje se ponavlja kasnije,
        // umesto da se lista označi kao očišćena a da se ništa ne skloni.
        if (series.isEmpty()) return@withContext false
        val versions = series.filter { it.tmdbId != null && it.tmdbId in autoTmdbIds }.distinctBy { it.id }
        val tmdbOf = versions.associate { it.id to it.tmdbId!! }
        val favorites = favoriteDao.getByPlaylist(playlistId).filter { it.kind == KIND_SERIES && it.itemId in tmdbOf }
        val groups = favorites.groupBy { tmdbOf.getValue(it.itemId) }.filterValues { it.size > 1 }
        if (groups.isEmpty()) return@withContext true
        val watched = progressDao.seriesWithProgress(playlistId).toSet()
        val categories = categoryNames(VodKind.SERIES)
        val byTmdb = versions.groupBy { it.tmdbId!! }
        db.withTransaction {
            for ((tmdbId, saved) in groups) {
                val all = byTmdb.getValue(tmdbId)
                if (all.any { it.id in watched }) continue
                val chosen = preferredVersions(all, watched, categories).single()
                for (favorite in saved) {
                    if (favorite.itemId != chosen.id) favoriteDao.delete(playlistId, KIND_SERIES, favorite.itemId)
                }
                favoriteDao.insertMissing(
                    listOf(
                        VodFavoriteEntity(
                            playlistId = playlistId,
                            kind = KIND_SERIES,
                            itemId = chosen.id,
                            name = chosen.name,
                            poster = chosen.poster,
                            extension = null,
                            addedAt = saved.maxOf { it.addedAt },
                        ),
                    ),
                )
            }
        }
        true
    }

    /** Nazivi kategorija filmova, odnosno serija, po ID-ju; prazno kad ih panel trenutno ne šalje. */
    private suspend fun categoryNames(kind: VodKind): Map<String, String> = try {
        allCategories(kind).associate { it.id to it.name }
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        emptyMap()
    }

    private val VodKind.favoriteKind: String get() = if (this == VodKind.MOVIES) KIND_MOVIE else KIND_SERIES

    private suspend fun session(): Session =
        session(settings.activePlaylistId.first() ?: throw VodUnavailableException())

    private suspend fun session(playlistId: Long): Session {
        val playlist = playlistDao.getById(playlistId) ?: throw VodUnavailableException()
        if (playlist.type != PlaylistType.XTREAM) throw VodUnavailableException()
        val server = XtreamUrls.normalizeServer(playlist.source) ?: throw VodUnavailableException()
        val username = playlist.username ?: throw VodUnavailableException()
        val password = try {
            cipher.decrypt(playlist.passwordEncrypted ?: throw VodUnavailableException())
        } catch (e: VodUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Stored password can not be read", e)
        }
        return Session(playlistId, server, username, password, playlist.userAgent, cachePrefix(playlistId, playlist.lastRefreshedAt), playlist.lastRefreshedAt ?: 0)
    }

    /** Spisak jedne kategorije, ili cele liste kad je [categoryId] [ALL_CATEGORIES]. */
    private fun Session.listUrl(action: String, categoryId: String): String =
        if (categoryId == ALL_CATEGORIES) api(action) else api(action, "category_id", categoryId)

    private fun Session.api(action: String): String = XtreamUrls.playerApi(server, username, password, action)

    private fun Session.api(action: String, parameter: String, value: String): String =
        XtreamUrls.playerApi(server, username, password, action, parameter, value)

    private fun WatchProgressEntity.toDomain() = WatchProgress(itemId, positionMs, durationMs, watched = watchedAt != null, updatedAt = updatedAt)

    /**
     * Spisak iz memorije, inače sa uređaja (ako je čuvanje uključeno), inače sa servera. Spisak sa
     * uređaja preuzet pre poslednjeg osvežavanja liste se prikazuje odmah, a noviji se preuzima u
     * pozadini i javlja kroz [updates].
     *
     * Prazan spisak se ne pamti: server ga ponekad vrati kad zapne (preopterećen, ograničen broj
     * veza), pa bi Filmovi i Serije ostali prazni sve do sledećeg osvežavanja liste. Zato se prazan
     * spisak sa servera ne upisuje ni u memoriju ni na uređaj, ne zamenjuje sačuvani spisak, a prazan
     * spisak sačuvan ranije se ne koristi, nego se pita server.
     */
    private suspend fun <V : List<*>> cached(
        memory: ConcurrentHashMap<String, V>,
        key: String,
        kind: VodKind,
        readDisk: () -> VodCached<V>?,
        writeDisk: (V) -> Unit,
        stamp: Long,
        fetch: suspend () -> V,
    ): V {
        memory[key]?.let { return it }
        // Isti spisak često zatraži više ekrana odjednom (Početna, pretraga, Trakt): preuzima ga prvi,
        // a ostali sačekaju njegov ishod, umesto da svako preuzima ceo spisak za sebe.
        return loading.getOrPut(key) { Mutex() }.withLock {
            memory[key]?.let { return@withLock it }
            val local = settings.vodLocalCache.first()
            if (local) {
                val stored = readDisk()
                if (stored != null && stored.value.isNotEmpty()) {
                    memory[key] = stored.value
                    if (stored.stamp != stamp) refreshInBackground(memory, key, kind, writeDisk, fetch)
                    return@withLock stored.value
                }
            }
            val value = fetch()
            if (value.isNotEmpty()) {
                memory[key] = value
                if (local) writeDisk(value)
            }
            value
        }
    }

    /** Po jedna brava za svaki spisak koji se upravo učitava, vidi [cached]. */
    private val loading = ConcurrentHashMap<String, Mutex>()

    private fun <V : List<*>> refreshInBackground(
        memory: ConcurrentHashMap<String, V>,
        key: String,
        kind: VodKind,
        writeDisk: (V) -> Unit,
        fetch: suspend () -> V,
    ) {
        if (!refreshing.add(key)) return
        writeScope.launch {
            try {
                val value = fetch()
                // Prazan spisak ne briše sačuvani, vidi [cached].
                if (value.isEmpty()) return@launch
                // Ako je lista u međuvremenu ponovo osvežena, ključ je izbačen i ovaj spisak je zastareo.
                if (memory.replace(key, value) == null) return@launch
                if (settings.vodLocalCache.first()) writeDisk(value)
                _updates.emit(kind)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bez mreže ostaje sačuvani spisak; pokušava se opet pri sledećem otvaranju.
            } finally {
                refreshing.remove(key)
            }
        }
    }

    private suspend fun <V : Any> ConcurrentHashMap<String, V>.getOrPutSuspending(key: String, load: suspend () -> V): V {
        this[key]?.let { return it }
        val value = load()
        this[key] = value
        return value
    }

    private companion object {
        const val KIND_MOVIE = "movie"
        const val KIND_EPISODE = "episode"
        const val KIND_SERIES = "series"

        /** Oznaka za spisak bez kategorije (svi filmovi, odnosno sve serije). */
        const val ALL_CATEGORIES = ""

        /** Razlika pozicija za koju se ne menja zapamćeno (zaokruživanje procenta na Trakt-u). */
        const val SAME_POSITION_MS = 30_000L

        /** Koliko poslednje gledanih serija, odnosno filmova, se proverava pri dolasku pozicije sa drugog uređaja. */
        const val REMOTE_MATCH_LIMIT = 10

        /** Najviše stavki u redu „Nastavi gledanje”. */
        const val CONTINUE_LIMIT = 20

        /** Koliko redova se čita za „Nastavi gledanje” pre spajanja jezičkih verzija. */
        const val CONTINUE_ROWS = 100
    }
}
