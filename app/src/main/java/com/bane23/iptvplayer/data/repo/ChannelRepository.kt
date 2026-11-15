package com.bane23.iptvplayer.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import androidx.room.withTransaction
import com.bane23.iptvplayer.data.db.ChannelEntity
import com.bane23.iptvplayer.data.db.ChannelWithFavorite
import com.bane23.iptvplayer.data.db.FavoriteEntity
import com.bane23.iptvplayer.data.db.HistoryEntity
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.xtream.XtreamUrls
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.domain.StreamSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Ulaz UI sloja za kategorije, kanale i adrese strimova. */
@Singleton
class ChannelRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val cipher: PasswordCipher,
) {
    private val playlistDao = db.playlistDao()
    private val categoryDao = db.categoryDao()
    private val channelDao = db.channelDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()

    val activePlaylistId: Flow<Long?> = settings.activePlaylistId

    suspend fun playlistName(playlistId: Long): String? = playlistDao.getById(playlistId)?.name

    fun observeCategories(playlistId: Long): Flow<List<Category>> =
        categoryDao.observeLiveWithCount(playlistId).map { rows ->
            rows.map { Category(it.category.id, it.category.name, it.channelCount) }
        }

    /** Sve kategorije, i sakrivene, za ekran uređivanja. */
    fun observeAllCategories(playlistId: Long): Flow<List<Category>> =
        categoryDao.observeAllLiveWithCount(playlistId).map { rows ->
            rows.map { Category(it.category.id, it.category.name, it.channelCount, it.category.hidden) }
        }

    suspend fun setCategoryHidden(categoryId: Long, hidden: Boolean) = categoryDao.setHidden(categoryId, hidden)

    /** Pomera kategoriju za jedno mesto gore ([delta] = -1) ili dole (+1) i pamti ceo redosled. */
    suspend fun moveCategory(playlistId: Long, categoryId: Long, delta: Int) = db.withTransaction {
        val ordered = categoryDao.getLive(playlistId).toMutableList()
        val from = ordered.indexOfFirst { it.id == categoryId }
        val to = from + delta
        if (from < 0 || to !in ordered.indices) return@withTransaction
        ordered.add(to, ordered.removeAt(from))
        ordered.forEachIndexed { index, category ->
            if (category.userOrder != index) categoryDao.setUserOrder(category.id, index)
        }
    }

    fun observeChannelCount(playlistId: Long): Flow<Int> = channelDao.observeCountByPlaylist(playlistId)

    fun observeFavoriteCount(playlistId: Long): Flow<Int> = channelDao.observeFavoriteCount(playlistId)

    fun observeRecentCount(playlistId: Long): Flow<Int> = channelDao.observeRecentCount(playlistId)

    /**
     * Kanali jedne kategorije (ili cele liste kad je [categoryId] null), učitavani stranu po stranu.
     * [categoryId] može biti i posebna grupa iz [ChannelGroups].
     * [startIndex] je redni broj kanala od kog lista treba da krene (npr. kanal koji se trenutno gleda).
     */
    fun channels(playlistId: Long, categoryId: Long?, startIndex: Int = 0): Flow<PagingData<Channel>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true, jumpThreshold = PAGE_SIZE * 3),
            initialKey = (startIndex - PAGE_SIZE / 2).coerceAtLeast(0),
            pagingSourceFactory = {
                when (categoryId) {
                    ChannelGroups.FAVORITES -> channelDao.favoritesPagingSource(playlistId)
                    ChannelGroups.RECENT -> channelDao.recentPagingSource(playlistId)
                    else -> channelDao.pagingSource(playlistId, categoryId)
                }
            },
        ).flow.map { page -> page.map { it.toDomain() } }

    /** Kanali čiji naziv sadrži [query], kroz sve kategorije liste. */
    fun search(playlistId: Long, query: String): Flow<PagingData<Channel>> {
        val text = query.trim()
        if (text.isEmpty()) return flowOf(PagingData.empty())
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true),
            pagingSourceFactory = { channelDao.searchPagingSource(playlistId, "%$escaped%") },
        ).flow.map { page -> page.map { it.toDomain() } }
    }

    /** ID-jevi kanala posebne grupe (favoriti, nedavno gledano), redom kojim se prikazuju. */
    suspend fun groupChannelIds(playlistId: Long, groupId: Long): List<Long> = when (groupId) {
        ChannelGroups.FAVORITES -> channelDao.favoriteIds(playlistId)
        ChannelGroups.RECENT -> channelDao.recentIds(playlistId)
        else -> emptyList()
    }

    fun observeFavorite(channel: Channel): Flow<Boolean> = flowOfKey(channel.id) { key ->
        favoriteDao.observeExists(channel.playlistId, key)
    }

    /** Dodaje kanal u favorite ili ga uklanja iz njih. */
    suspend fun toggleFavorite(channelId: Long) = db.withTransaction {
        val channel = channelDao.getById(channelId) ?: return@withTransaction
        if (favoriteDao.exists(channel.playlistId, channel.stableKey)) {
            favoriteDao.delete(channel.playlistId, channel.stableKey)
        } else {
            favoriteDao.insert(FavoriteEntity(channel.playlistId, channel.stableKey, System.currentTimeMillis()))
        }
    }

    /** Beleži da je kanal upravo gledan. */
    suspend fun markWatched(channelId: Long) = db.withTransaction {
        val channel = channelDao.getById(channelId) ?: return@withTransaction
        historyDao.upsert(HistoryEntity(channel.playlistId, channel.stableKey, System.currentTimeMillis()))
        historyDao.trim(channel.playlistId, HISTORY_SIZE)
    }

    val resumeLastChannel: Flow<Boolean> = settings.resumeLastChannel

    suspend fun setResumeLastChannel(enabled: Boolean) = settings.setResumeLastChannel(enabled)

    /** Kanal koji treba pustiti pri startu aplikacije, ako je to uključeno u podešavanjima. */
    suspend fun channelToResume(): Long? {
        if (!settings.resumeLastChannel.first()) return null
        val playlistId = settings.activePlaylistId.first() ?: return null
        return channelDao.recentIds(playlistId).firstOrNull()
    }

    private fun flowOfKey(channelId: Long, block: (String) -> Flow<Boolean>): Flow<Boolean> =
        flow {
            val key = channelDao.getById(channelId)?.stableKey
            if (key == null) emit(false) else emitAll(block(key))
        }

    suspend fun getChannel(id: Long): Channel? = channelDao.getById(id)?.toDomain()

    /** Redni broj kanala (od nule) u svojoj kategoriji, odnosno u celoj listi. */
    suspend fun positionOf(channel: Channel, categoryId: Long?): Int =
        channelDao.positionOf(channel.playlistId, categoryId, channel.sortOrder)

    /** Sledeći kanal u istoj kategoriji; posle poslednjeg ide prvi. */
    suspend fun next(channel: Channel, categoryId: Long?): Channel? =
        (channelDao.next(channel.playlistId, categoryId, channel.sortOrder)
            ?: channelDao.first(channel.playlistId, categoryId))?.toDomain()

    /** Prethodni kanal u istoj kategoriji; pre prvog ide poslednji. */
    suspend fun previous(channel: Channel, categoryId: Long?): Channel? =
        (channelDao.previous(channel.playlistId, categoryId, channel.sortOrder)
            ?: channelDao.last(channel.playlistId, categoryId))?.toDomain()

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

    private fun ChannelWithFavorite.toDomain() = channel.toDomain().copy(favorite = favorite)

    private fun ChannelEntity.toDomain() = Channel(
        id = id,
        playlistId = playlistId,
        categoryId = categoryId,
        name = name,
        logo = logo,
        number = number,
        sortOrder = sortOrder,
    )

    private companion object {
        const val PAGE_SIZE = 60
        const val HISTORY_SIZE = 50
    }
}
