package com.bane23.iptvplayer.ui.common.vod

import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.Season
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodePauseImagesTest {

    private fun episode(image: String?) = Episode(
        id = 1,
        season = 1,
        number = 1,
        title = "Prva",
        extension = "mkv",
        plot = null,
        image = image,
        durationSeconds = null,
    )

    private fun series(backdrops: List<String>) = SeriesDetails(
        name = "Izmišljena serija",
        poster = null,
        plot = null,
        genre = null,
        cast = null,
        releaseDate = null,
        rating = null,
        seasons = listOf(Season(1, listOf(episode(null)))),
        backdrops = backdrops,
    )

    @Test
    fun episodeImageComesFirstWithoutRepeats() {
        val images = episodePauseImages(
            episode(" https://slike.invalid/e1.jpg "),
            series(listOf("https://slike.invalid/s1.jpg", "https://slike.invalid/e1.jpg")),
        )
        assertEquals(listOf("https://slike.invalid/e1.jpg", "https://slike.invalid/s1.jpg"), images)
    }

    @Test
    fun seriesImagesWhenEpisodeHasNoUsableImage() {
        assertEquals(
            listOf("https://slike.invalid/s1.jpg"),
            episodePauseImages(episode("nije link"), series(listOf("https://slike.invalid/s1.jpg"))),
        )
        assertEquals(emptyList<String>(), episodePauseImages(episode(null), series(emptyList())))
    }
}
