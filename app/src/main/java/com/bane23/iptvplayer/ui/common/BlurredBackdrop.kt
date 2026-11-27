package com.bane23.iptvplayer.ui.common

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.Transformation
import kotlin.math.hypot
import kotlinx.coroutines.delay

/**
 * Razlivena pozadina (čuvar ekrana, ekran pauze): jedna slika razlivena preko celog ekrana i
 * zatamnjena. Na svaki minut se sporo pretopi u sledeću. Razlivanje i zatamnjenje se rade jednom, na
 * maloj slici, pa se na TV-u crta samo jedna slika i to samo dok traje pretapanje. Sa [shuffle] se
 * slike smenjuju nasumično, inače redom. Dok se nijedna ne učita, ne crta se ništa.
 *
 * Pri pretapanju stara slika ostaje potpuno neprovidna, a nova se preko nje pojavljuje. Da obe
 * istovremeno blede (kao `Crossfade`), na sredini prelaza bi se kroz njih nazirao kadar ispod
 * (Bane, 27. 11. 2026, ekran pauze).
 */
@Composable
fun BlurredBackdrop(images: List<String>, modifier: Modifier = Modifier, shuffle: Boolean = true) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(images) {
        if (images.isEmpty()) return@LaunchedEffect
        val order = if (shuffle) images.shuffled() else images
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
                // Sa jednom slikom nema šta da se smenjuje.
                if (order.size == 1) return@LaunchedEffect
                delay(BACKDROP_CHANGE_MS)
            } else {
                failedInRow++
            }
        }
    }
    // Slika koja je bila pre tekuće; crta se ispod nje samo dok traje pretapanje.
    var below by remember { mutableStateOf<ImageBitmap?>(null) }
    var top by remember { mutableStateOf<ImageBitmap?>(null) }
    val topAlpha = remember { Animatable(1f) }
    LaunchedEffect(shown) {
        val next = shown ?: return@LaunchedEffect
        below = top
        top = next
        // Prva slika se pojavljuje preko kadra kao i ranije, svaka sledeća preko prethodne.
        topAlpha.snapTo(0f)
        topAlpha.animateTo(1f, tween(BACKDROP_FADE_MS))
        below = null
    }
    Box(modifier = modifier) {
        below?.let { BackdropImage(it, Modifier) }
        top?.let { BackdropImage(it, Modifier.graphicsLayer { alpha = topAlpha.value }) }
    }
}

@Composable
private fun BackdropImage(image: ImageBitmap, modifier: Modifier) {
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Već razlivena mala slika: obično razvlačenje je dovoljno glatko.
        filterQuality = FilterQuality.Low,
        modifier = modifier.fillMaxSize(),
    )
}

/** Iseca sredinu postera u odnosu 16:9 na maloj slici, razliva je i zatamnjuje (jače ka ivicama). */
private object BlurDimTransformation : Transformation() {
    override val cacheKey: String = "screensaverBlurDim1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input.config == Bitmap.Config.HARDWARE
        val software = if (hardware) input.copy(Bitmap.Config.ARGB_8888, false) else input
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
