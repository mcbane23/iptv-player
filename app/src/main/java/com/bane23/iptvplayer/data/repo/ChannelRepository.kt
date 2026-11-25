package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.db.ProgramHit
import com.bane23.iptvplayer.data.db.ChannelHit
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import androidx.room.withTransaction
import com.bane23.iptvplayer.data.db.ChannelEntity
import com.bane23.iptvplayer.data.db.ChannelQualityEntity
import com.bane23.iptvplayer.data.db.HiddenChannelEntity
import com.bane23.iptvplayer.data.db.ChannelWithFavorite
import com.bane23.iptvplayer.data.db.FavoriteEntity
import com.bane23.iptvplayer.data.db.FavoriteListEntity
import com.bane23.iptvplayer.data.db.FavoriteListItemEntity
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.domain.FavoriteSections
import com.bane23.iptvplayer.data.db.HistoryEntity
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.text.SearchText
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.CategoryArrange
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelFilter
import com.bane23.iptvplayer.domain.ChannelRules
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.domain.StreamSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Jedan izvor kanala: kanal [channelId] iz liste [playlistName], vidi [ChannelRepository.sources]. */
data class ChannelSource(
    val channelId: Long,
    val playlistId: Long,
    val playlistName: String,
    /** Naziv kanala ovog izvora; razlikuje se od naziva kanala koji se gleda kad je izvor druga verzija (kvalitet). */
    val name: String = "",
    /** Izmerena visina slike (npr. 720); 0 dok izvor nijednom nije pušten ni izmeren. */
    val height: Int = 0,
    /** Izmeren protok u bitovima u sekundi; 0 dok nije izmeren. */
    val bitrate: Int = 0,
)

/** Ulaz UI sloja za kategorije, kanale i adrese strimova. */
@Singleton
class ChannelRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val cipher: PasswordCipher,
    private val locks: LockRepository,
) {
    private val playlistDao = db.playlistDao()
    private val categoryDao = db.categoryDao()
    private val channelDao = db.channelDao()
    private val favoriteDao = db.favoriteDao()
    private val hiddenChannelDao = db.hiddenChannelDao()
    private val favoriteListDao = db.favoriteListDao()
    private val historyDao = db.historyDao()
    private val qualityDao = db.channelQualityDao()

    val activePlaylistId: Flow<Long?> = settings.activePlaylistId

    suspend fun activePlaylistIdNow(): Long? = settings.activePlaylistId.first()

    suspend fun playlistName(playlistId: Long): String? = playlistDao.getById(playlistId)?.name

    /**
     * Vidljive kategorije. Kategorija koja je sada zaključana ima katanac ispred naziva. Dok je uključen
     * filter kanala ili neko pravilo za kanale, kategorije u kojima posle toga nema nijednog kanala se
     * ne prikazuju.
     */
    fun observeCategories(playlistId: Long): Flow<List<Category>> =
        combine(
            categoryDao.observeLiveWithCount(playlistId),
            locks.activeKeys,
            settings.liveFilter,
            settings.channelRules,
        ) { rows, lockedKeys, filter, rules ->
            val dropEmpty = filter.active || rules.any
            rows.filter { !dropEmpty || it.channelCount > 0 }.map {
                val name = it.category.name
                val locked = LockRepository.liveKey(it.category.playlistId, name) in lockedKeys
                Category(
                    id = it.category.id,
                    name = if (locked) LockRepository.LOCK_MARK + name else name,
                    channelCount = it.channelCount,
                    playlistId = it.category.playlistId,
                    locked = locked,
                )
            }
        }

    /** Sve kategorije, i sakrivene, za ekran uređivanja. */
    fun observeAllCategories(playlistId: Long): Flow<List<Category>> =
        categoryDao.observeAllLiveWithCount(playlistId).map { rows ->
            rows.map { Category(it.category.id, it.category.name, it.channelCount, it.category.hidden, it.category.playlistId) }
        }

    suspend fun setCategoryHidden(categoryId: Long, hidden: Boolean) = categoryDao.setHidden(categoryId, hidden)

    suspend fun setAllCategoriesHidden(playlistId: Long, hidden: Boolean) =
        categoryDao.setAllLiveHidden(playlistId, hidden)

    /** Sakriva ili prikazuje samo zadate kategorije (npr. one koje je pretraga našla). */
    suspend fun setCategoriesHidden(categoryIds: List<Long>, hidden: Boolean) = db.withTransaction {
        categoryIds.forEach { categoryDao.setHidden(it, hidden) }
    }

    /** Pamti ceo redosled kategorija; [orderedIds] su ID-jevi svih kategorija liste, redom prikaza. */
    suspend fun setCategoryOrder(playlistId: Long, orderedIds: List<Long>) = db.withTransaction {
        val current = categoryDao.getLive(playlistId).associate { it.id to it.userOrder }
        orderedIds.forEachIndexed { index, id ->
            if (id in current && current[id] != index) categoryDao.setUserOrder(id, index)
        }
    }

    /** Prebacuje kategoriju na vrh spiska. */
    suspend fun moveCategoryToTop(playlistId: Long, categoryId: Long) {
        val ids = categoryDao.getLive(playlistId).map { it.id }
        val index = ids.indexOf(categoryId)
        if (index > 0) setCategoryOrder(playlistId, CategoryArrange.movedToTop(ids, index))
    }

    /** Kategorija otvorena poslednji put u Uživo (vidi `LiveTvViewModel`), ili null. */
    suspend fun lastCategory(playlistId: Long): String? = settings.lastCategory(playlistId, SECTION_LIVE).first()

    suspend fun setLastCategory(playlistId: Long, value: String) =
        settings.setLastCategory(playlistId, SECTION_LIVE, value)

    /** Da li je lista Xtream nalog (takve liste imaju i filmove i serije). */
    fun observeIsXtream(playlistId: Long): Flow<Boolean> =
        playlistDao.observeById(playlistId).map { it?.type == PlaylistType.XTREAM }

    fun observeChannelCount(playlistId: Long): Flow<Int> = channelDao.observeVisibleCount(playlistId)

    fun observeFavoriteCount(playlistId: Long): Flow<Int> = channelDao.observeFavoriteCount(playlistId)

    fun observeRecentCount(playlistId: Long): Flow<Int> = channelDao.observeRecentCount(playlistId)

    /**
     * Kanali jedne kategorije (ili cele liste kad je [categoryId] null), učitavani stranu po stranu.
     * [categoryId] može biti i posebna grupa iz [ChannelGroups].
     * [startIndex] je redni broj kanala od kog lista treba da krene (npr. kanal koji se trenutno gleda).
     */
    fun channels(playlistId: Long, categoryId: Long?, startIndex: Int = 0): Flow<PagingData<Channel>> =
        if (categoryId == ChannelGroups.FAVORITES) favoriteChannels(playlistId, startIndex) else Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true, jumpThreshold = PAGE_SIZE * 3),
            initialKey = (startIndex - PAGE_SIZE / 2).coerceAtLeast(0),
            pagingSourceFactory = {
                val now = System.currentTimeMillis()
                val listId = ChannelGroups.favoriteListId(categoryId)
                when {
                    listId != null -> channelDao.favoriteListPagingSource(playlistId, listId, now)
                    categoryId == ChannelGroups.RECENT -> channelDao.recentPagingSource(playlistId, now)
                    else -> channelDao.pagingSource(playlistId, categoryId, now)
                }
            },
        ).flow.map { page -> page.map { it.toDomain() } }

    /** Omiljeni kanali, sa sekcijom u kojoj je svaki ([Channel.favoriteSection]). */
    private fun favoriteChannels(playlistId: Long, startIndex: Int): Flow<PagingData<Channel>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true, jumpThreshold = PAGE_SIZE * 3),
            initialKey = (startIndex - PAGE_SIZE / 2).coerceAtLeast(0),
            pagingSourceFactory = { channelDao.favoritesPagingSource(playlistId, System.currentTimeMillis()) },
        ).flow.map { page -> page.map { it.row.toDomain().copy(favoriteSection = it.sectionId) } }

    /** Kanali čiji naziv sadrži [query], kroz sve kategorije liste. */
    fun search(playlistId: Long, query: String): Flow<PagingData<Channel>> {
        val text = query.trim()
        if (text.isEmpty()) return flowOf(PagingData.empty())
        val pattern = likePattern(text)
        return flow {
            // Kanali koji odgovaraju uz promašeno slovo nalaze se jednom, pre listanja rezultata.
            val nearIds = nearChannels(playlistId, text, NEAR_LIMIT).map { it.id }
            emitAll(
                Pager(
                    config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true),
                    pagingSourceFactory = {
                        channelDao.searchPagingSource(playlistId, pattern, nearIds, System.currentTimeMillis())
                    },
                ).flow.map { page -> page.map { it.toDomain() } },
            )
        }
    }

    /**
     * Kanali čiji naziv ne sadrži [query] slovo po slovo, ali ga sadrži kad se zanemare kvačice ili
     * jedno promašeno slovo; najviše [limit]. Tačne pogotke daje baza, pa se ovde preskaču.
     */
    private suspend fun nearChannels(playlistId: Long, query: String, limit: Int): List<ChannelHit> {
        val text = query.trim()
        val needle = SearchText.normalize(text)
        if (needle.isEmpty() || limit <= 0) return emptyList()
        val names = channelDao.searchNames(playlistId)
        return withContext(Dispatchers.Default) {
            names.asSequence()
                .filter { !it.name.contains(text, ignoreCase = true) }
                .filter { hit -> SearchText.normalize(hit.name).let { needle in it || SearchText.near(it, needle) } }
                .take(limit)
                .toList()
        }
    }

    /** Filter kanala u Uživo (kvalitet, zemlja). Kanale koji mu ne odgovaraju sklanja `ChannelRulesSync`. */
    val liveFilter: Flow<ChannelFilter> get() = settings.liveFilter

    suspend fun setLiveFilter(filter: ChannelFilter) = settings.setLiveFilter(filter)

    /** Oznake zemalja koje liste zaista koriste ispred naziva kanala ili kategorije, najčešće prve. */
    suspend fun filterCountries(): List<String> {
        return channelDao.countries(ChannelRules.MIN_COUNTRY_CHANNELS)
    }

    private fun likePattern(query: String): String =
        "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    /** Prvih [limit] kanala čiji naziv sadrži [query], za zajedničku pretragu. */
    suspend fun searchTop(playlistId: Long, query: String, limit: Int): List<ChannelHit> {
        val exact = channelDao.searchTop(playlistId, likePattern(query), limit)
        if (exact.size >= limit) return exact
        val known = exact.mapTo(HashSet()) { it.id }
        return exact + nearChannels(playlistId, query, limit).filter { it.id !in known }.take(limit - exact.size)
    }

    /** Emisije iz vodiča (u toku ili buduće) čiji naslov sadrži [query]. */
    suspend fun searchPrograms(playlistId: Long, query: String, limit: Int): List<ProgramHit> =
        channelDao.searchPrograms(playlistId, likePattern(query), System.currentTimeMillis(), limit)

    /**
     * Kanali za red na „Početnoj” na TV-u: omiljeni, a kad ih nema, nedavno gledani. Prva vrednost
     * je grupa iz [ChannelGroups] kojoj kanali pripadaju, da plejer lista kroz istu grupu.
     */
    suspend fun homeChannels(playlistId: Long, limit: Int): Pair<Long, List<Channel>> {
        val now = System.currentTimeMillis()
        val favorites = channelDao.favoritesTop(playlistId, now, limit)
        if (favorites.isNotEmpty()) return ChannelGroups.FAVORITES to favorites.map { it.toDomain() }
        return ChannelGroups.RECENT to channelDao.recentTop(playlistId, now, limit).map { it.toDomain() }
    }

    /** ID-jevi kanala posebne grupe (favoriti, nedavno gledano), redom kojim se prikazuju. */
    suspend fun groupChannelIds(playlistId: Long, groupId: Long): List<Long> = when (groupId) {
        ChannelGroups.FAVORITES -> channelDao.favoriteIds(playlistId)
        ChannelGroups.RECENT -> channelDao.recentIds(playlistId)
        else -> ChannelGroups.favoriteListId(groupId)?.let { channelDao.favoriteListIds(playlistId, it) }.orEmpty()
    }

    fun observeFavorite(channel: Channel): Flow<Boolean> = flowOfKey(channel.id) { key ->
        favoriteDao.observeInAnyList(channel.playlistId, key)
    }

    /** Sekcije omiljenih, sa brojem kanala vidljivih iz liste [playlistId]. */
    fun observeFavoriteLists(playlistId: Long): Flow<List<FavoriteList>> = observeFavoriteSections(playlistId).map { it.sections }

    /** Sekcije omiljenih i broj omiljenih kanala bez sekcije, onako kako ih spisak „Omiljeni” prikazuje. */
    fun observeFavoriteSections(playlistId: Long): Flow<FavoriteSections> =
        combine(favoriteListDao.observeAll(), channelDao.observeFavoriteSectionCounts(playlistId)) { lists, counts ->
            val byId = counts.associate { it.sectionId to it.channelCount }
            FavoriteSections(
                unsorted = byId[null] ?: 0,
                sections = lists.map { FavoriteList(it.id, it.name, byId[it.id] ?: 0) },
            )
        }

    /** Kanal je među omiljenima (sa sekcijom ili bez nje). */
    suspend fun isFavorite(channelId: Long): Boolean {
        val channel = channelDao.getById(channelId) ?: return false
        return favoriteDao.exists(channel.playlistId, channel.stableKey) ||
            favoriteListDao.listsOf(channel.playlistId, channel.stableKey).isNotEmpty()
    }

    suspend fun hasFavoriteLists(): Boolean = favoriteListDao.count() > 0

    /** Sekcija u kojoj je kanal, ili null kad je bez sekcije (ili nije među omiljenima). */
    suspend fun favoriteSectionOf(channelId: Long): Long? {
        val channel = channelDao.getById(channelId) ?: return null
        return favoriteListDao.listsOf(channel.playlistId, channel.stableKey).firstOrNull()
    }

    /**
     * Pravi listu omiljenih sa imenom [name]. Vraća njen ID, ili null kad je ime prazno ili već
     * postoji lista sa tim imenom.
     */
    suspend fun createFavoriteList(name: String): Long? = db.withTransaction {
        val clean = name.trim()
        if (clean.isEmpty() || favoriteListDao.nameTaken(clean, 0)) return@withTransaction null
        favoriteListDao.insert(FavoriteListEntity(name = clean, position = favoriteListDao.nextPosition()))
    }

    /** Menja ime liste. Vraća false kad je ime prazno ili zauzeto. */
    suspend fun renameFavoriteList(listId: Long, name: String): Boolean = db.withTransaction {
        val clean = name.trim()
        if (clean.isEmpty() || favoriteListDao.nameTaken(clean, listId)) return@withTransaction false
        favoriteListDao.rename(listId, clean)
        true
    }

    /** Briše sekciju. Njeni kanali ostaju među omiljenima, bez sekcije. */
    suspend fun deleteFavoriteList(listId: Long) = favoriteListDao.delete(listId)

    /** Pomera sekciju za jedno mesto gore ([delta] = -1) ili dole (1). */
    suspend fun moveFavoriteList(listId: Long, delta: Int) = db.withTransaction {
        val lists = favoriteListDao.all().toMutableList()
        val index = lists.indexOfFirst { it.id == listId }
        val target = index + delta
        if (index < 0 || target !in lists.indices) return@withTransaction
        lists.add(target, lists.removeAt(index))
        lists.forEachIndexed { position, list -> favoriteListDao.setPosition(list.id, position) }
    }

    /**
     * Stavlja kanal među omiljene, u sekciju [sectionId] (null = bez sekcije). Kanal je u najviše jednoj
     * sekciji, pa izlazi iz one u kojoj je bio; mesto u spisku određuje trenutak premeštanja.
     */
    suspend fun setFavoriteSection(channelId: Long, sectionId: Long?) = db.withTransaction {
        val channel = channelDao.getById(channelId) ?: return@withTransaction
        val now = System.currentTimeMillis()
        favoriteDao.insert(FavoriteEntity(channel.playlistId, channel.stableKey, now))
        val current = favoriteListDao.listsOf(channel.playlistId, channel.stableKey)
        if (current == listOfNotNull(sectionId)) return@withTransaction
        favoriteListDao.deleteItems(channel.playlistId, channel.stableKey)
        if (sectionId != null) {
            favoriteListDao.insertItem(FavoriteListItemEntity(sectionId, channel.playlistId, channel.stableKey, now))
        }
    }

    /** Dodaje kanal u omiljene (bez sekcije) ili ga uklanja iz njih, zajedno sa sekcijom. */
    suspend fun toggleFavorite(channelId: Long) = db.withTransaction {
        val channel = channelDao.getById(channelId) ?: return@withTransaction
        if (isFavorite(channelId)) {
            favoriteDao.delete(channel.playlistId, channel.stableKey)
            favoriteListDao.deleteItems(channel.playlistId, channel.stableKey)
        } else {
            favoriteDao.insert(FavoriteEntity(channel.playlistId, channel.stableKey, System.currentTimeMillis()))
        }
    }

    /** Sakriva kanal iz svih spiskova; vraća se iz podešavanja ([showChannel]). */
    suspend fun hideChannel(channelId: Long) {
        val channel = channelDao.getById(channelId) ?: return
        hiddenChannelDao.insert(
            HiddenChannelEntity(channel.playlistId, channel.stableKey, channel.name, System.currentTimeMillis()),
        )
    }

    suspend fun showChannel(playlistId: Long, channelKey: String) = hiddenChannelDao.delete(playlistId, channelKey)

    /** Sakriveni kanali liste (i lista spojenih sa njom), za spisak u podešavanjima. */
    fun observeHiddenChannels(playlistId: Long): Flow<List<HiddenChannelEntity>> = hiddenChannelDao.observe(playlistId)

    /** Beleži da je kanal upravo gledan. */
    suspend fun markWatched(channelId: Long) = db.withTransaction {
        val channel = channelDao.getById(channelId) ?: return@withTransaction
        // Kanal iz zaključane kategorije se ne pamti, ni kad se gleda posle unosa PIN-a.
        if (locks.isInLockedCategory(channel.playlistId, channel.stableKey)) return@withTransaction
        historyDao.upsert(HistoryEntity(channel.playlistId, channel.stableKey, System.currentTimeMillis()))
        historyDao.trim(channel.playlistId, HISTORY_SIZE)
    }

    /** Uklanja kanal iz nedavno gledanih. */
    suspend fun removeFromRecent(channelId: Long) {
        val channel = channelDao.getById(channelId) ?: return
        historyDao.delete(channel.playlistId, channel.stableKey)
    }

    /** Briše sve nedavno gledane kanale aktivne liste i lista spojenih sa njom. */
    suspend fun clearRecent() {
        val playlistId = settings.activePlaylistId.first() ?: return
        historyDao.clearScope(playlistId)
    }

    val resumeLastChannel: Flow<Boolean> = settings.resumeLastChannel

    suspend fun setResumeLastChannel(enabled: Boolean) = settings.setResumeLastChannel(enabled)

    /** Kanal koji treba pustiti pri startu aplikacije, ako je to uključeno u podešavanjima. */
    suspend fun channelToResume(): Long? {
        if (!settings.resumeLastChannel.first()) return null
        val playlistId = settings.activePlaylistId.first() ?: return null
        // Kanal iz zaključane kategorije se ne pušta sam pri startu.
        return channelDao.recentIds(playlistId).firstOrNull()?.takeUnless { locks.isChannelLocked(it) }
    }

    /** Kanal koji je gledan poslednji u aktivnoj listi, ili null ako ništa nije gledano. */
    suspend fun lastWatchedChannelId(): Long? {
        val playlistId = settings.activePlaylistId.first() ?: return null
        return channelDao.recentIds(playlistId).firstOrNull()
    }

    private fun flowOfKey(channelId: Long, block: (String) -> Flow<Boolean>): Flow<Boolean> =
        flow {
            val key = channelDao.getById(channelId)?.stableKey
            if (key == null) emit(false) else emitAll(block(key))
        }

    /** ID kanala po stalnom ključu (ID se menja kad se lista osveži), ili null ako ga više nema. */
    suspend fun channelIdByKey(playlistId: Long, stableKey: String): Long? =
        channelDao.byStableKey(playlistId, stableKey)?.id

    suspend fun getChannel(id: Long): Channel? = channelDao.getById(id)?.toDomain()

    /**
     * Javlja se kad kanala sa ovim ID-jem više nema u bazi. Osvežavanje liste briše kanale i upisuje ih
     * iznova, pa svi dobiju nov ID; ko drži kanal duže (plejer), tada ga traži ponovo, vidi [refreshed].
     */
    fun observeGone(channelId: Long): Flow<Unit> =
        channelDao.observeExists(channelId).filter { exists -> !exists }.map { }

    /**
     * Isti kanal posle osvežavanja liste: nađen po stalnom ključu, na mestu najbližem starom. Vraća
     * [channel] ako je i dalje u bazi, a null ako ga na listi više nema.
     */
    suspend fun refreshed(channel: Channel): Channel? {
        channelDao.getById(channel.id)?.let { return it.toDomain() }
        if (channel.stableKey.isEmpty()) return null
        return channelDao.nearestByStableKey(channel.playlistId, channel.stableKey, channel.sortOrder)?.toDomain()
    }

    /** Redni broj kanala (od nule) u svojoj kategoriji, odnosno u celoj listi. */
    suspend fun positionOf(channel: Channel, categoryId: Long?): Int =
        channelDao.positionOf(channel.playlistId, categoryId, channel.playlistId, channel.sortOrder)

    /** Sledeći kanal u istoj kategoriji; posle poslednjeg ide prvi. */
    suspend fun next(channel: Channel, categoryId: Long?): Channel? =
        (channelDao.next(channel.playlistId, categoryId, channel.playlistId, channel.sortOrder)
            ?: channelDao.first(channel.playlistId, categoryId))?.toDomain()

    /** Prethodni kanal u istoj kategoriji; pre prvog ide poslednji. */
    suspend fun previous(channel: Channel, categoryId: Long?): Channel? =
        (channelDao.previous(channel.playlistId, categoryId, channel.playlistId, channel.sortOrder)
            ?: channelDao.last(channel.playlistId, categoryId))?.toDomain()

    /**
     * Izvori kanala. Prvi je uvek sam [channel]; slede kanali sa potpuno istim nazivom u listama
     * spojenim sa njegovom (po jedan iz svake liste), pa ostale verzije istog kanala: isti kanal u
     * drugom kvalitetu, u bilo kojoj kategoriji tih lista. Uz svaki izvor ide izmeren kvalitet, ako se zna.
     */
    suspend fun sources(channel: Channel): List<ChannelSource> {
        val variants = channelDao.variants(channel.playlistId, channel.id, MAX_VARIANTS)
        val known = variants.associateBy { it.id }
        val sameName = channelDao.sources(channel.playlistId, channel.name)
        val result = ArrayList<ChannelSource>()
        val seenIds = HashSet<Long>()
        // Isti strim u više kategorija (isti stableKey) je jedan izvor.
        val seenStreams = HashSet<Pair<Long, String>>()
        fun add(id: Long, playlistId: Long, playlistName: String, name: String) {
            if (result.size >= MAX_SOURCES || id in seenIds) return
            val row = known[id]
            if (row != null && !seenStreams.add(row.playlistId to row.stableKey)) return
            seenIds.add(id)
            result += ChannelSource(id, playlistId, playlistName, name, row?.height ?: 0, row?.bitrate ?: 0)
        }
        val own = known[channel.id]
        val ownPlaylist = sameName.firstOrNull { it.playlistId == channel.playlistId }?.playlistName ?: own?.playlistName.orEmpty()
        if (channel.stableKey.isNotEmpty()) seenStreams.add(channel.playlistId to channel.stableKey)
        seenIds.add(channel.id)
        result += ChannelSource(channel.id, channel.playlistId, ownPlaylist, channel.name, own?.height ?: 0, own?.bitrate ?: 0)
        sameName.filter { it.playlistId != channel.playlistId }.forEach { add(it.id, it.playlistId, it.playlistName, channel.name) }
        variants.forEach { add(it.id, it.playlistId, it.playlistName, it.name) }
        return result
    }

    /** Pamti stvarnu rezoluciju kanala (izmerenu dok se pušta). Protok ostaje samo ako se rezolucija nije promenila. */
    suspend fun saveQuality(channelId: Long, width: Int, height: Int) {
        if (height <= 0) return
        val channel = channelDao.getById(channelId) ?: return
        val old = qualityDao.get(channel.playlistId, channel.stableKey)
        qualityDao.put(
            ChannelQualityEntity(
                playlistId = channel.playlistId,
                stableKey = channel.stableKey,
                width = width,
                height = height,
                bitrate = old?.takeIf { it.height == height }?.bitrate ?: 0,
                measuredAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Pamti izmeren protok kanala kome je rezolucija već upisana. */
    suspend fun saveBitrate(channelId: Long, bitrate: Int) {
        if (bitrate <= 0) return
        val channel = channelDao.getById(channelId) ?: return
        qualityDao.setBitrate(channel.playlistId, channel.stableKey, bitrate)
    }

    suspend fun byNumber(playlistId: Long, number: Int): Channel? =
        channelDao.byNumber(playlistId, number)?.toDomain()

    /** Sastavlja adrese strima. Za Xtream se link pravi tek ovde, iz šifrovane lozinke. */
    suspend fun streamSource(channelId: Long): StreamSource? = withContext(Dispatchers.IO) {
        val channel = channelDao.getById(channelId) ?: return@withContext null
        val playlist = playlistDao.getById(channel.playlistId) ?: return@withContext null
        val userAgent = channel.userAgent ?: playlist.userAgent
        val urls = if (playlist.type == PlaylistType.XTREAM && channel.xtreamStreamId != null) {
            val server = XtreamUrls.normalizeServer(playlist.source) ?: return@withContext null
            val username = playlist.username ?: return@withContext null
            val password = try {
                cipher.decrypt(playlist.passwordEncrypted ?: return@withContext null)
            } catch (_: Exception) {
                return@withContext null
            }
            // Većina servera daje MPEG-TS; ako to ne radi, proba se HLS.
            listOf("ts", "m3u8").map { ext ->
                XtreamUrls.liveStream(server, username, password, channel.xtreamStreamId, ext)
            }
        } else {
            listOfNotNull(channel.streamUrl)
        }
        if (urls.isEmpty()) null else StreamSource(urls, userAgent, channel.referrer)
    }

    private fun ChannelWithFavorite.toDomain() = channel.toDomain().copy(
        favorite = favorite,
        nowTitle = nowTitle,
        nowStartMs = nowStartMs,
        nowStopMs = nowStopMs,
    )

    private fun ChannelEntity.toDomain() = Channel(
        id = id,
        playlistId = playlistId,
        categoryId = categoryId,
        name = name,
        logo = logo,
        number = number,
        sortOrder = sortOrder,
        archiveDays = ArchiveRepository.archiveDays(this),
        stableKey = stableKey,
    )

    private companion object {
        const val PAGE_SIZE = 60

        /** Najviše kanala koji se u pretrazi nude uz promašeno slovo (ograničenje broja vrednosti u upitu). */
        const val NEAR_LIMIT = 200
        const val HISTORY_SIZE = 50

        /** Najviše verzija istog kanala koje se čitaju iz baze, i najviše izvora koji se nude u plejeru. */
        const val MAX_VARIANTS = 40
        const val MAX_SOURCES = 12
        const val SECTION_LIVE = "live"
    }
}
