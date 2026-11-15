package com.bane23.iptvplayer.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.ChannelEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Ulaz UI sloja za kategorije, kanale i adrese strimova. */
@Singleton
class ChannelRepository @Inject constructor(
    db: AppDatabase,
    private val settings: SettingsStore,
    private val cipher: PasswordCipher,
) {
    private val playlistDao = db.playlistDao()
    private val categoryDao = db.categoryDao()
    private val channelDao = db.channelDao()

    val activePlaylistId: Flow<Long?> = settings.activePlaylistId

    suspend fun playlistName(playlistId: Long): String? = playlistDao.getById(playlistId)?.name

    fun observeCategories(playlistId: Long): Flow<List<Category>> =
        categoryDao.observeLiveWithCount(playlistId).map { rows ->
            rows.map { Category(it.category.id, it.category.name, it.channelCount) }
        }

    fun observeChannelCount(playlistId: Long): Flow<Int> = channelDao.observeCountByPlaylist(playlistId)

    /**
     * Kanali jedne kategorije (ili cele liste kad je [categoryId] null), učitavani stranu po stranu.
     * [startIndex] je redni broj kanala od kog lista treba da krene (npr. kanal koji se trenutno gleda).
     */
    fun channels(playlistId: Long, categoryId: Long?, startIndex: Int = 0): Flow<PagingData<Channel>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true, jumpThreshold = PAGE_SIZE * 3),
            initialKey = (startIndex - PAGE_SIZE / 2).coerceAtLeast(0),
            pagingSourceFactory = { channelDao.pagingSource(playlistId, categoryId) },
        ).flow.map { page -> page.map { it.toDomain() } }

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
    }
}
