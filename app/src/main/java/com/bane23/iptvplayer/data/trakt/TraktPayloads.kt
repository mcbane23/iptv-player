package com.bane23.iptvplayer.data.trakt

import com.bane23.iptvplayer.data.db.TraktPendingEntity
import com.bane23.iptvplayer.data.db.TraktWatchedEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Film ili epizoda onako kako ih Trakt prepoznaje: TMDB ID filma, odnosno serije, i broj sezone i epizode. */
data class TraktKey(val kind: String, val tmdbId: Long, val season: Int = 0, val episode: Int = 0) {
    companion object {
        const val MOVIE = "movie"
        const val EPISODE = "episode"

        fun movie(tmdbId: Long) = TraktKey(MOVIE, tmdbId)

        fun episode(showTmdbId: Long, season: Int, episode: Int) = TraktKey(EPISODE, showTmdbId, season, episode)
    }
}

/** Pretvaranje između Trakt odgovora i zahteva i onoga što aplikacija čuva. Bez mreže, da bi moglo da se testira. */
object TraktPayloads {

    /** Telo za dodavanje u istoriju ([watchedAt] zadat) ili uklanjanje iz nje ([watchedAt] null). */
    fun history(keys: Collection<TraktKey>, watchedAt: String?): HistoryBody {
        val movies = keys.filter { it.kind == TraktKey.MOVIE }
            .distinct()
            .map { HistoryMovie(TraktIds(it.tmdbId), watchedAt) }
        val shows = keys.filter { it.kind == TraktKey.EPISODE }
            .distinct()
            .groupBy { it.tmdbId }
            .map { (show, episodes) ->
                HistoryShow(
                    ids = TraktIds(show),
                    seasons = episodes.groupBy { it.season }.toSortedMap().map { (season, list) ->
                        HistorySeason(season, list.sortedBy { it.episode }.map { HistoryEpisode(it.episode, watchedAt) })
                    },
                )
            }
        return HistoryBody(movies = movies, shows = shows)
    }

    /** Sve odgledano sa naloga kao redovi za bazu; stavke bez TMDB ID-ja se preskaču. */
    fun watchedRows(movies: List<WatchedMovie>, shows: List<WatchedShow>, now: Long): List<TraktWatchedEntity> {
        val rows = ArrayList<TraktWatchedEntity>()
        for (movie in movies) {
            val tmdb = movie.movie?.ids?.tmdb ?: continue
            rows.add(TraktWatchedEntity(TraktKey.MOVIE, tmdb, 0, 0, now))
        }
        for (show in shows) {
            val tmdb = show.show?.ids?.tmdb ?: continue
            for (season in show.seasons) {
                for (episode in season.episodes) {
                    rows.add(TraktWatchedEntity(TraktKey.EPISODE, tmdb, season.number, episode.number, now))
                }
            }
        }
        return rows.distinctBy { listOf(it.kind, it.tmdbId, it.season, it.episode) }
    }

    /**
     * Od više promena iste stavke važi samo poslednja (npr. označeno pa poništeno ne šalje ništa
     * osim poništavanja). Redosled ostaje redosled poslednjih promena.
     */
    fun latestChanges(pending: List<TraktPendingEntity>): List<TraktPendingEntity> =
        pending.sortedBy { it.id }
            .associateBy { listOf(it.playlistId, it.kind, it.itemId, it.seriesId) }
            .values
            .sortedBy { it.id }

    /** Telo za `scrobble/pause`. */
    fun scrobble(key: TraktKey, progress: Float): ScrobbleBody =
        if (key.kind == TraktKey.MOVIE) {
            ScrobbleBody(movie = TraktMedia(TraktIds(key.tmdbId)), progress = progress)
        } else {
            ScrobbleBody(show = TraktMedia(TraktIds(key.tmdbId)), episode = ScrobbleEpisode(key.season, key.episode), progress = progress)
        }

    /** Ključ započetog gledanja sa naloga, ili null kad nema TMDB ID. */
    fun playbackKey(item: PlaybackItem): TraktKey? {
        item.movie?.ids?.tmdb?.let { return TraktKey.movie(it) }
        val show = item.show?.ids?.tmdb ?: return null
        val episode = item.episode ?: return null
        return TraktKey.episode(show, episode.season, episode.number)
    }

    /** Vreme iz Trakt odgovora (`2026-11-16T09:30:00.000Z`) u milisekundama, ili null. */
    fun parseTime(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")) {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            try {
                return format.parse(text)?.time
            } catch (_: java.text.ParseException) {
                // Probati sledeći oblik.
            }
        }
        return null
    }

    /** Vreme u obliku koji Trakt očekuje, npr. `2026-11-16T09:30:00.000Z`. */
    fun isoTime(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }
}
