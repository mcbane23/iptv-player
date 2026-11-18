package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class XtreamVodStream(
    val name: FlexString = null,
    /** ID filma na TMDB-u, ako ga panel šalje; služi za poklapanje sa Trakt-om. */
    val tmdb: FlexString = null,
    @SerialName("tmdb_id") val tmdbId: FlexString = null,
    @SerialName("stream_id") val streamId: FlexString = null,
    @SerialName("stream_icon") val icon: FlexString = null,
    @SerialName("category_id") val categoryId: FlexString = null,
    val rating: FlexString = null,
    @SerialName("container_extension") val extension: FlexString = null,
    /** Kada je film dodat (sekunde od 1970), ako panel šalje. */
    val added: FlexString = null,
    val year: FlexString = null,
    @SerialName("release_date") val releaseDate: FlexString = null,
)

@Serializable
data class XtreamSeries(
    val name: FlexString = null,
    /** ID serije na TMDB-u, ako ga panel šalje. */
    val tmdb: FlexString = null,
    @SerialName("tmdb_id") val tmdbId: FlexString = null,
    @SerialName("series_id") val seriesId: FlexString = null,
    val cover: FlexString = null,
    @SerialName("category_id") val categoryId: FlexString = null,
    val rating: FlexString = null,
    /** Kada je serija poslednji put dopunjena (sekunde od 1970), ako panel šalje. */
    @SerialName("last_modified") val lastModified: FlexString = null,
    val year: FlexString = null,
    val releaseDate: FlexString = null,
    @SerialName("release_date") val releaseDateAlt: FlexString = null,
)

/** Detalji filma, onoliko koliko je panel poslao. */
data class MovieDetails(
    val name: String?,
    val poster: String?,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val rating: String?,
    val durationSeconds: Long?,
    /** Nastavak fajla (mp4, mkv...), potreban za link strima. */
    val extension: String?,
    /** ID na TMDB-u, ako ga panel šalje. */
    val tmdbId: Long? = null,
    /** Široka slika iz filma za pozadinu detalja, ako je panel šalje. */
    val backdrop: String? = null,
    /** Link trejlera, vidi [XtreamVodParser.trailerUrl]. */
    val trailer: String? = null,
)

data class Episode(
    val id: Long,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val plot: String?,
    val image: String?,
    val durationSeconds: Long?,
)

data class Season(val number: Int, val episodes: List<Episode>)

data class SeriesDetails(
    val name: String?,
    val poster: String?,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val releaseDate: String?,
    val rating: String?,
    val seasons: List<Season>,
    /** ID serije na TMDB-u, ako ga panel šalje. */
    val tmdbId: Long? = null,
    /** Široka slika iz serije za pozadinu detalja, ako je panel šalje. */
    val backdrop: String? = null,
    /** Link trejlera, vidi [XtreamVodParser.trailerUrl]. */
    val trailer: String? = null,
) {
    /** Sve epizode redom: po sezoni, pa po broju epizode. */
    val episodes: List<Episode> get() = seasons.flatMap { it.episodes }

    /** Epizoda koja sledi posle date, ili null ako je poslednja. */
    fun episodeAfter(episodeId: Long): Episode? {
        val all = episodes
        val index = all.indexOfFirst { it.id == episodeId }
        return if (index < 0) null else all.getOrNull(index + 1)
    }
}

/**
 * Čita odgovore `get_vod_info` i `get_series_info`. Paneli se ovde najviše razlikuju: prazno
 * `info` stiže kao `[]`, brojevi kao tekst, a epizode čas kao mapa po sezonama, čas kao niz nizova.
 * Zato se odgovor čita ručno, polje po polje, i sve što nedostaje ostaje null.
 */
object XtreamVodParser {

    fun movie(json: JsonElement): MovieDetails {
        val root = json as? JsonObject
        val info = root?.get("info") as? JsonObject
        val data = root?.get("movie_data") as? JsonObject
        return MovieDetails(
            name = data.text("name") ?: info.text("name"),
            poster = info.text("movie_image") ?: info.text("cover_big") ?: info.text("cover"),
            plot = info.text("plot") ?: info.text("description"),
            genre = info.text("genre"),
            cast = info.text("cast") ?: info.text("actors"),
            director = info.text("director"),
            releaseDate = info.text("releasedate") ?: info.text("release_date") ?: info.text("releaseDate"),
            rating = info.text("rating"),
            durationSeconds = info.text("duration_secs")?.toLongOrNull()?.takeIf { it > 0 },
            extension = data.text("container_extension"),
            tmdbId = listOf(info.text("tmdb_id"), info.text("tmdb"), data.text("tmdb_id"), data.text("tmdb"))
                .firstNotNullOfOrNull(::tmdbId),
            backdrop = info.image("backdrop_path"),
            trailer = trailerUrl(info.text("youtube_trailer") ?: info.text("trailer")),
        )
    }

    fun series(json: JsonElement): SeriesDetails {
        val root = json as? JsonObject
        val info = root?.get("info") as? JsonObject
        val episodes = ArrayList<Episode>()
        when (val raw = root?.get("episodes")) {
            // {"1": [epizode], "2": [epizode]}
            is JsonObject -> for ((season, list) in raw) readEpisodes(list, season.toIntOrNull(), episodes)
            // [[epizode prve sezone], [epizode druge sezone]]
            is JsonArray -> raw.forEachIndexed { index, list -> readEpisodes(list, index + 1, episodes) }
            else -> Unit
        }
        val seasons = episodes.groupBy { it.season }
            .toSortedMap()
            .map { (number, list) -> Season(number, list.sortedBy { it.number }) }
        return SeriesDetails(
            name = info.text("name"),
            poster = info.text("cover") ?: info.text("cover_big"),
            plot = info.text("plot"),
            genre = info.text("genre"),
            cast = info.text("cast"),
            releaseDate = info.text("releaseDate") ?: info.text("releasedate") ?: info.text("release_date"),
            rating = info.text("rating"),
            seasons = seasons,
            tmdbId = tmdbId(info.text("tmdb")) ?: tmdbId(info.text("tmdb_id")),
            backdrop = info.image("backdrop_path"),
            trailer = trailerUrl(info.text("youtube_trailer") ?: info.text("trailer")),
        )
    }

    private fun readEpisodes(list: JsonElement, seasonFromKey: Int?, out: MutableList<Episode>) {
        if (list !is JsonArray) return
        list.forEachIndexed { index, element ->
            val episode = element as? JsonObject ?: return@forEachIndexed
            val id = episode.text("id")?.toLongOrNull() ?: return@forEachIndexed
            val info = episode["info"] as? JsonObject
            val number = episode.text("episode_num")?.toIntOrNull() ?: (index + 1)
            out.add(
                Episode(
                    id = id,
                    season = seasonFromKey ?: episode.text("season")?.toIntOrNull() ?: 1,
                    number = number,
                    title = episode.text("title") ?: number.toString(),
                    extension = episode.text("container_extension") ?: DEFAULT_EXTENSION,
                    plot = info.text("plot"),
                    image = info.text("movie_image") ?: info.text("cover_big"),
                    durationSeconds = info.text("duration_secs")?.toLongOrNull()?.takeIf { it > 0 },
                ),
            )
        }
    }

    /** Vrednost polja kao tekst; prazno, `null` i složene vrednosti se računaju kao da polja nema. */
    private fun JsonObject?.text(key: String): String? {
        val value = this?.get(key)
        if (value == null || value is JsonNull || value !is JsonPrimitive) return null
        return value.content.trim().takeIf { it.isNotEmpty() }
    }

    /** Link slike iz polja koje stiže čas kao tekst, čas kao niz linkova (uzima se prvi). */
    private fun JsonObject?.image(key: String): String? {
        val value = this?.get(key)
        val first = if (value is JsonArray) value.firstOrNull { it is JsonPrimitive && it !is JsonNull } else value
        if (first == null || first is JsonNull || first !is JsonPrimitive) return null
        return first.content.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    /**
     * Link trejlera iz polja `youtube_trailer`: paneli šalju ili ceo link ili samo YouTube oznaku
     * snimka. Sve drugo (prazno, tekst sa razmacima) znači da trejlera nema.
     */
    fun trailerUrl(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        return when {
            text.startsWith("https://") || text.startsWith("http://") -> text
            YOUTUBE_ID.matches(text) -> "https://www.youtube.com/watch?v=$text"
            else -> null
        }
    }

    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{6,20}")

    /** TMDB ID kao broj; paneli umesto praznog šalju i "0". */
    fun tmdbId(text: String?): Long? = text?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    const val DEFAULT_EXTENSION = "mp4"
}
