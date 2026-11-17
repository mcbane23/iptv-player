package com.bane23.iptvplayer.data.repo

import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.FavoriteEntity
import com.bane23.iptvplayer.data.db.PlaylistEntity
import com.bane23.iptvplayer.data.lan.TransferAccounts
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCategory
import com.bane23.iptvplayer.data.lan.TransferLogin
import com.bane23.iptvplayer.data.lan.TransferPlaylist
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.lan.TransferSettings
import com.bane23.iptvplayer.data.lan.TransferTraktLogin
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesRepository
import com.bane23.iptvplayer.data.trakt.TokenResponse
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.domain.PlaylistDraft
import com.bane23.iptvplayer.domain.PlaylistException
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.player.BufferSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
) {
    suspend fun exportBundle(): TransferExport = withContext(Dispatchers.IO) {
        var skipped = 0
        val items = db.playlistDao().getAll().sortedBy { it.id }.mapNotNull { entity ->
            val item = entity.toTransfer()
            if (item == null) skipped++
            item
        }
        val player = settings.playerSettings.first()
        val bundle = TransferBundle(
            playlists = items,
            settings = TransferSettings(
                resumeLastChannel = settings.resumeLastChannel.first(),
                buffer = player.buffer.name,
                audioLanguage = player.audioLanguage,
                subtitleLanguage = player.subtitleLanguage,
            ),
            accounts = openSubtitles.credentials()?.let { (username, password) ->
                TransferAccounts(openSubtitles = TransferLogin(username, password))
            },
        )
        TransferExport(bundle, skipped)
    }

    /** Lista iz fajla ostaje na telefonu, kao i lista čija lozinka ne može da se pročita. */
    private suspend fun PlaylistEntity.toTransfer(): TransferPlaylist? {
        val type = when (type) {
            PlaylistType.XTREAM -> TransferPlaylist.TYPE_XTREAM
            PlaylistType.M3U_URL -> TransferPlaylist.TYPE_M3U
            PlaylistType.M3U_FILE -> return null
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
            source = source,
            username = username.orEmpty(),
            password = password,
            userAgent = userAgent.orEmpty(),
            epgUrl = epgUrlOverride.orEmpty(),
            epgShiftHours = epgShiftHours,
            favorites = db.favoriteDao().keysByPlaylist(id),
            categories = categories,
        )
    }

    /** [onProgress] javlja koja se lista upravo učitava (redni broj od 1, ukupno). */
    suspend fun importBundle(bundle: TransferBundle, onProgress: (Int, Int) -> Unit = { _, _ -> }): TransferResult =
        withContext(Dispatchers.IO) {
            var added = 0
            var existing = 0
            var failed = 0
            bundle.playlists.forEachIndexed { index, item ->
                onProgress(index + 1, bundle.playlists.size)
                val type = when (item.type) {
                    TransferPlaylist.TYPE_XTREAM -> PlaylistType.XTREAM
                    TransferPlaylist.TYPE_M3U -> PlaylistType.M3U_URL
                    else -> null
                }
                if (type == null) {
                    failed++
                    return@forEachIndexed
                }
                // Čita se svaki put iznova, da se ista lista dvaput u paketu ne doda dvaput.
                val match = db.playlistDao().getAll().firstOrNull { it.matches(type, item) }
                val playlistId = if (match != null) {
                    existing++
                    match.id
                } else {
                    try {
                        playlists.addPlaylist(item.toDraft(type)).also { added++ }
                    } catch (_: PlaylistException) {
                        failed++
                        return@forEachIndexed
                    }
                }
                val now = System.currentTimeMillis()
                item.favorites.take(MAX_FAVORITES).forEachIndexed { position, key ->
                    // Redosled dodavanja se čuva kroz vreme upisa.
                    db.favoriteDao().insert(FavoriteEntity(playlistId, key, now + position))
                }
                // Uređenje kategorija se primenjuje samo na novu listu; na postojećoj važi ono sa TV-a.
                if (match == null) applyCategories(playlistId, item.categories)
            }
            bundle.settings?.let { applySettings(it) }
            val accounts = bundle.accounts
            TransferResult(
                added = added,
                existing = existing,
                failed = failed,
                openSubtitles = accounts?.openSubtitles?.let { adoptOpenSubtitles(it) },
                trakt = accounts?.trakt?.let { adoptTrakt(it) },
            )
        }

    private suspend fun adoptOpenSubtitles(login: TransferLogin): Boolean {
        if (login.username.isBlank() || login.username.length > MAX_ACCOUNT_FIELD || login.password.length > MAX_ACCOUNT_FIELD) {
            return false
        }
        return openSubtitles.adoptLogin(login.username, login.password)
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

    private fun PlaylistEntity.matches(type: PlaylistType, item: TransferPlaylist): Boolean =
        this.type == type &&
            source.trimEnd('/').equals(item.source.trim().trimEnd('/'), ignoreCase = true) &&
            username.orEmpty() == item.username.trim()

    private fun TransferPlaylist.toDraft(type: PlaylistType) = PlaylistDraft(
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

    private suspend fun applySettings(incoming: TransferSettings) {
        settings.setResumeLastChannel(incoming.resumeLastChannel)
        BufferSize.entries.firstOrNull { it.name == incoming.buffer }?.let { settings.setPlayerBuffer(it) }
        settings.setAudioLanguage(incoming.audioLanguage.take(MAX_LANGUAGE_LENGTH))
        settings.setSubtitleLanguage(incoming.subtitleLanguage.take(MAX_LANGUAGE_LENGTH))
    }

    private companion object {
        const val MAX_FAVORITES = 5_000
        const val MAX_LANGUAGE_LENGTH = 16
        const val MAX_ACCOUNT_FIELD = 2_000
    }
}
