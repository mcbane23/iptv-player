package com.bane23.iptvplayer.ui.tv

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.Transformation
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Posteri za pozadinu čuvara ekrana: omiljeni filmovi i serije aktivne liste, a ako ih nema,
 * poslednje gledani. Zaključani naslovi (roditeljska zaštita) se ne prikazuju.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScreensaverViewModel @Inject constructor(vod: VodRepository) : ViewModel() {
    val posters: StateFlow<List<String>> = vod.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    vod.observeFavorites(playlistId, VodKind.MOVIES),
                    vod.observeFavorites(playlistId, VodKind.SERIES),
                    vod.observeContinue(playlistId, VodKind.MOVIES),
                    vod.observeContinue(playlistId, VodKind.SERIES),
                ) { movies, series, continueMovies, continueSeries ->
                    screensaverPosters(
                        favorites = movies.map { it.poster } + series.map { it.poster },
                        recent = (continueMovies + continueSeries).sortedByDescending { it.updatedAt }.map { it.poster },
                    )
                }
            }
        }
        // Bez postera čuvar ostaje crn, kao ranije.
        .catch { if (it is CancellationException) throw it else emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Omiljeni posteri; ako ih nema, poslednje gledani. Bez praznih i ponovljenih adresa. */
internal fun screensaverPosters(favorites: List<String?>, recent: List<String?>): List<String> {
    fun List<String?>.usable() = mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    return favorites.usable().ifEmpty { recent.usable() }
}

/**
 * Pozadina čuvara: jedan poster razliven preko celog ekrana i zatamnjen. Na svaki minut se sporo
 * pretopi u sledeći. Razlivanje i zatamnjenje se rade jednom, na maloj slici, pa se na TV-u crta
 * samo jedna slika i to samo dok traje pretapanje.
 */
@Composable
fun ScreensaverBackdrop(posters: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(posters) {
        if (posters.isEmpty()) return@LaunchedEffect
        val order = posters.shuffled()
        var index = 0
        var failedInRow = 0
        while (failedInRow < order.size) {
            val request = ImageRequest.Builder(context)
                .data(order[index % order.size])
                .size(BACKDROP_WIDTH * 2, BACKDROP_HEIGHT * 4)
                .transformations(BlurDimTransformation)
                .build()
            index++
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                failedInRow = 0
                shown = result.image.toBitmap().asImageBitmap()
                // Sa jednim posterom nema šta da se smenjuje.
                if (order.size == 1) return@LaunchedEffect
                delay(BACKDROP_CHANGE_MS)
            } else {
                failedInRow++
            }
        }
    }
    Crossfade(targetState = shown, animationSpec = tween(BACKDROP_FADE_MS), label = "screensaverBackdrop", modifier = modifier) { image ->
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Već razlivena mala slika: obično razvlačenje je dovoljno glatko.
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Iseca sredinu postera u odnosu 16:9 na maloj slici, razliva je i zatamnjuje (jače ka ivicama). */
private object BlurDimTransformation : Transformation() {
    override val cacheKey: String = "screensaverBlurDim1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val software = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val cropHeight = (software.width * BACKDROP_HEIGHT / BACKDROP_WIDTH).coerceAtMost(software.height)
        val cropWidth = (cropHeight * BACKDROP_WIDTH / BACKDROP_HEIGHT).coerceAtMost(software.width)
        val cropped = Bitmap.createBitmap(
            software,
            (software.width - cropWidth) / 2,
            (software.height - cropHeight) / 2,
            cropWidth.coerceAtLeast(1),
            cropHeight.coerceAtLeast(1),
        )
        val small = Bitmap.createScaledBitmap(cropped, BACKDROP_WIDTH, BACKDROP_HEIGHT, true)
        val pixels = IntArray(BACKDROP_WIDTH * BACKDROP_HEIGHT)
        small.getPixels(pixels, 0, BACKDROP_WIDTH, 0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT)
        blurAndDim(pixels, BACKDROP_WIDTH, BACKDROP_HEIGHT)
        return Bitmap.createBitmap(pixels, BACKDROP_WIDTH, BACKDROP_HEIGHT, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Razliva sliku sa tri prolaza prosečnog zamućenja (blizu Gausovog) i zatamnjuje je: u sredini na
 * [CENTER_BRIGHTNESS], ka uglovima do [EDGE_BRIGHTNESS]. Rezultat je neprovidan.
 */
internal fun blurAndDim(pixels: IntArray, width: Int, height: Int, radius: Int = BLUR_RADIUS) {
    val size = width * height
    val r = IntArray(size) { (pixels[it] shr 16) and 0xFF }
    val g = IntArray(size) { (pixels[it] shr 8) and 0xFF }
    val b = IntArray(size) { pixels[it] and 0xFF }
    val temp = IntArray(size)
    repeat(BLUR_PASSES) {
        for (channel in arrayOf(r, g, b)) {
            boxBlur(channel, temp, width, height, radius, horizontal = true)
            boxBlur(temp, channel, width, height, radius, horizontal = false)
        }
    }
    val cx = (width - 1) / 2f
    val cy = (height - 1) / 2f
    val maxDistance = hypot(cx, cy).coerceAtLeast(1f)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            val t = (hypot(x - cx, y - cy) / maxDistance).coerceIn(0f, 1f)
            val k = CENTER_BRIGHTNESS + (EDGE_BRIGHTNESS - CENTER_BRIGHTNESS) * t * t
            pixels[i] = (0xFF shl 24) or
                ((r[i] * k).toInt().coerceIn(0, 255) shl 16) or
                ((g[i] * k).toInt().coerceIn(0, 255) shl 8) or
                (b[i] * k).toInt().coerceIn(0, 255)
        }
    }
}

/** Prosek prozora širine 2·radius+1 u jednom pravcu; na ivicama se ponavlja krajnja tačka. */
private fun boxBlur(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val window = 2 * radius + 1
    for (line in 0 until lines) {
        fun at(pos: Int): Int {
            val p = pos.coerceIn(0, length - 1)
            return if (horizontal) src[line * width + p] else src[p * width + line]
        }
        var sum = 0
        for (p in -radius..radius) sum += at(p)
        for (pos in 0 until length) {
            val out = if (horizontal) line * width + pos else pos * width + line
            dst[out] = sum / window
            sum += at(pos + radius + 1) - at(pos - radius)
        }
    }
}

private const val BACKDROP_WIDTH = 96
private const val BACKDROP_HEIGHT = 54
private const val BLUR_RADIUS = 4
private const val BLUR_PASSES = 3
private const val CENTER_BRIGHTNESS = 0.5f
private const val EDGE_BRIGHTNESS = 0.22f
private const val BACKDROP_CHANGE_MS = 60_000L
private const val BACKDROP_FADE_MS = 2_500
