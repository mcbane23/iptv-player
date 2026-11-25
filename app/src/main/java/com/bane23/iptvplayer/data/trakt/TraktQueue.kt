package com.bane23.iptvplayer.data.trakt

import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.TraktPendingEntity
import com.bane23.iptvplayer.data.db.TraktPlaybackPendingEntity
import com.bane23.iptvplayer.work.TraktScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Beleži promene oznake „odgledano” koje treba poslati na Trakt i zakazuje slanje. Kad prijave
 * na Trakt nema, ne radi ništa.
 */
@Singleton
class TraktQueue @Inject constructor(
    db: AppDatabase,
    private val tokens: TraktTokenStore,
    private val scheduler: TraktScheduler,
) {
    private val dao = db.traktDao()

    /** [kind] je "movie" ili "episode"; [seriesId] je 0 za film. */
    suspend fun changed(playlistId: Long, kind: String, itemIds: List<Long>, seriesId: Long, watched: Boolean) {
        if (itemIds.isEmpty() || !tokens.isAvailable || !tokens.isSignedIn()) return
        val now = System.currentTimeMillis()
        dao.addPending(itemIds.map { TraktPendingEntity(0, playlistId, kind, it, seriesId, watched, now) })
        scheduler.syncSoon()
    }

    /** Gledanje je prekinuto na [progress] procenata: pozicija ide na Trakt, za nastavak na drugom uređaju. */
    suspend fun playbackChanged(playlistId: Long, kind: String, itemId: Long, seriesId: Long, progress: Float) {
        if (!tokens.isAvailable || !tokens.isSignedIn()) return
        dao.setPlayback(TraktPlaybackPendingEntity(playlistId, kind, itemId, seriesId, progress, System.currentTimeMillis()))
        // Samo pozicija, bez spiskova odgledanog: kratko je, pa drugi uređaj odmah ima novo mesto.
        scheduler.sendPlaybackNow()
    }

    /** Da li pozicija filma ili epizode još čeka slanje na Trakt. */
    suspend fun hasPendingPlayback(playlistId: Long, kind: String, itemId: Long): Boolean =
        dao.pendingPlaybackCount(playlistId, kind, itemId) > 0

    /** Odbacuje poziciju filma ili epizode koja još čeka slanje (korisnik ne želi nastavak). */
    suspend fun dropPlayback(playlistId: Long, kind: String, itemId: Long) = dao.deletePlayback(playlistId, kind, itemId)

    /** Odbacuje pozicije svih epizoda serije koje još čekaju slanje. */
    suspend fun dropPlaybackOfSeries(playlistId: Long, seriesId: Long) = dao.deleteSeriesPlayback(playlistId, seriesId)

    /**
     * Odmah skida oznaku koja je stigla sa Trakt-a, da kvačica nestane bez čekanja na slanje.
     * Sa naloga se uklanja kad se promena pošalje.
     */
    suspend fun forget(keys: List<TraktKey>) {
        for (key in keys) dao.deleteWatched(key.kind, key.tmdbId, key.season, key.episode)
    }
}
