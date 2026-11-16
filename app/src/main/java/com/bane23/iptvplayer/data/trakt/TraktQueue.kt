package com.bane23.iptvplayer.data.trakt

import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.TraktPendingEntity
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

    /**
     * Odmah skida oznaku koja je stigla sa Trakt-a, da kvačica nestane bez čekanja na slanje.
     * Sa naloga se uklanja kad se promena pošalje.
     */
    suspend fun forget(keys: List<TraktKey>) {
        for (key in keys) dao.deleteWatched(key.kind, key.tmdbId, key.season, key.episode)
    }
}
