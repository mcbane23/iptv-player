package com.bane23.iptvplayer.ui.common.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.ui.common.BlurredBackdrop
import com.bane23.iptvplayer.ui.common.rememberClockText
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Ekran pauze za film i epizodu, kao čuvar ekrana (Bane, 27. 11. 2026, izgled „B”): preko celog
 * ekrana razlivena slika baš tog naslova ([images], pa [poster]), a poster, sat i opis su u jednoj
 * pločici koja jednom u minuti preskoči na drugo mesto, da ništa ne stoji na istom mestu. Između
 * skokova se ništa ne pomera (neprekidno klizanje je na TV-u previše koštalo). Dok se nijedna slika
 * ne učita, ili ih naslov nema, ispod se vidi zatamnjen kadar kao ranije. U Staklu je pločica
 * staklena, u klasičnom izgledu neprovidna.
 *
 * Prvi minut se vidi stari ekran pauze ([PauseInfoScreen] preko zatamnjenog kadra), pa se tek onda
 * pretopi u čuvar ekrana (Bane, 27. 11. 2026). Odbrojavanje kreće iz početka svaki put kad se ekran
 * pauze ponovo pojavi.
 */
@Composable
fun BoxScope.PauseScreensaver(info: PauseInfo, poster: String?, images: List<String>, compact: Boolean) {
    var saver by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PLAIN_PAUSE_MS)
        saver = true
    }
    val saverShown by animateFloatAsState(
        targetValue = if (saver) 1f else 0f,
        animationSpec = tween(SWITCH_FADE_MS, easing = LinearEasing),
        label = "pauseSaver",
    )
    if (saverShown < 1f) {
        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = 1f - saverShown }) {
            PauseInfoScreen(info, compact)
        }
    }
    if (saver) {
        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = saverShown }) {
            SaverContent(info, poster, images, compact)
        }
    }
}

@Composable
private fun BoxScope.SaverContent(info: PauseInfo, poster: String?, images: List<String>, compact: Boolean) {
    val backgrounds = remember(images, poster) {
        (images + listOfNotNull(poster)).map(String::trim).filter(String::isNotEmpty).distinct()
    }
    BlurredBackdrop(backgrounds, modifier = Modifier.fillMaxSize(), shuffle = false)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Unutar margina zbog overscan-a televizora.
            .padding(horizontal = if (compact) 16.dp else 48.dp, vertical = if (compact) 16.dp else 27.dp),
    ) {
        JumpingBlock {
            PauseCard(info, poster, compact)
        }
    }
}

@Composable
private fun PauseCard(info: PauseInfo, poster: String?, compact: Boolean) {
    val clock by rememberClockText()
    var posterFailed by remember(poster) { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 28.dp),
        modifier = Modifier
            .widthIn(max = if (compact) 560.dp else 780.dp)
            .glass(Glass.CardShape, strong = true)
            .padding(if (compact) 14.dp else 24.dp),
    ) {
        if (!poster.isNullOrBlank() && !posterFailed) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { posterFailed = true },
                modifier = Modifier
                    .size(width = if (compact) 84.dp else 170.dp, height = if (compact) 126.dp else 255.dp)
                    .clip(Glass.PosterShape),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            BasicText(
                text = clock,
                maxLines = 1,
                style = TextStyle(color = Color.White, fontSize = if (compact) 26.sp else 44.sp, fontWeight = FontWeight.Light),
                modifier = Modifier.padding(bottom = if (compact) 2.dp else 6.dp),
            )
            PauseInfoTexts(info, compact, smallTitle = true)
        }
    }
}

/**
 * Sadržaj koji jednom u minuti nestane, preskoči na nasumično mesto u okviru i ponovo se pojavi.
 * Pomeranje i providnost su samo u fazi crtanja, bez ponovnog slaganja ekrana.
 */
@Composable
private fun BoxScope.JumpingBlock(content: @Composable () -> Unit) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    var block by remember { mutableStateOf(IntSize.Zero) }
    var x by remember { mutableFloatStateOf(Random.nextFloat()) }
    var y by remember { mutableFloatStateOf(Random.nextFloat()) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(JUMP_EVERY_MS)
            alpha.animateTo(0f, tween(JUMP_FADE_MS, easing = LinearEasing))
            x = Random.nextFloat()
            y = Random.nextFloat()
            alpha.animateTo(1f, tween(JUMP_FADE_MS, easing = LinearEasing))
        }
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { area = it },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { block = it }
                .graphicsLayer {
                    this.alpha = alpha.value
                    translationX = (area.width - block.width).coerceAtLeast(0) * x
                    translationY = (area.height - block.height).coerceAtLeast(0) * y
                },
        ) {
            content()
        }
    }
}

private const val JUMP_EVERY_MS = 60_000L
private const val JUMP_FADE_MS = 600
private const val PLAIN_PAUSE_MS = 60_000L
private const val SWITCH_FADE_MS = 1_000
