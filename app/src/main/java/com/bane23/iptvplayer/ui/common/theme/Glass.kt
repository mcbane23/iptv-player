package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Izgled „Staklo” (Bane, 23. 11. 2026): sve pločice u aplikaciji su poluprovidne, sa tankom svetlom
 * ivicom, kao meniji u plejeru. Pozadina se ne zamućuje (skupo na slabijim TV uređajima), pločica je
 * samo blago svetlija od onoga što je iza nje. Izabrano je providna boja aplikacije sa ivicom u toj
 * boji, a fokus na TV-u pločica puna u toj boji sa mekim oreolom. Svaki novi ekran koristi ove modifikatore
 * umesto svojih boja i okvira.
 */
object Glass {
    /** Podloga obične pločice. */
    val Fill = Color.White.copy(alpha = 0.06f)

    /** Podloga pločice koja treba da se izdvoji (meni, traka, pritisnuto). */
    val FillStrong = Color.White.copy(alpha = 0.09f)

    /** Tanka ivica svake pločice. */
    val Border = Color.White.copy(alpha = 0.11f)

    /** Red u spisku, dugme, polje. */
    val RowShape = RoundedCornerShape(14.dp)

    /** Kartica sa više redova (grupa podešavanja, lista, dijalog). */
    val CardShape = RoundedCornerShape(18.dp)

    /** Dugmad za izbor, meni u obliku kapsule. */
    val PillShape = RoundedCornerShape(50)

    /** Poster, slika epizode. */
    val PosterShape = RoundedCornerShape(12.dp)

    const val SELECTED_FILL = 0.20f
    const val SELECTED_BORDER = 0.65f
    const val HALO = 0.16f
}

/** Staklena pločica: poluprovidna podloga i tanka svetla ivica. */
fun Modifier.glass(shape: Shape = Glass.RowShape, strong: Boolean = false): Modifier =
    background(if (strong) Glass.FillStrong else Glass.Fill, shape).border(1.dp, Glass.Border, shape)

/** Izabrana pločica: providna boja aplikacije [accent] sa ivicom u toj boji. */
fun Modifier.glassSelected(accent: Color, shape: Shape = Glass.RowShape): Modifier =
    background(accent.copy(alpha = Glass.SELECTED_FILL), shape)
        .border(1.dp, accent.copy(alpha = Glass.SELECTED_BORDER), shape)

/**
 * Pločica sa fokusom na TV-u: cela u boji [accent], a sadržaj na njoj je taman (`onPrimary`). Bane je
 * to izabrao 23. 11. 2026, pošto se providan fokus sa okvirom na TV-u slabo razlikovao od izabrane
 * stavke; puna boja je i najjeftinija za crtanje. Oreol oko nje crta [focusHalo], koji mora da stoji
 * pre odsecanja (clip) u nizu modifikatora.
 */
fun Modifier.glassFocused(accent: Color, shape: Shape = Glass.RowShape): Modifier = background(accent, shape)

/**
 * Podloga pločice u jednom pozivu: fokus, izabrano ili obično staklo. Sa [plain] obična pločica nema
 * podlogu (npr. stavka u meniju koji je i sam staklen).
 */
fun Modifier.glassState(
    accent: Color,
    focused: Boolean,
    selected: Boolean,
    shape: Shape = Glass.RowShape,
    plain: Boolean = false,
): Modifier = when {
    focused -> glassFocused(accent, shape)
    selected -> glassSelected(accent, shape)
    plain -> this
    else -> glass(shape)
}

/**
 * Meki oreol u boji [accent] oko fokusirane pločice zaobljene za [radius]. Crta se van ivica pločice,
 * pa mora da stoji pre `clip` u nizu modifikatora. Jedan providan zaobljen pravougaonik, bez
 * zamućenja, da ne košta ništa pri pomeranju fokusa.
 */
fun Modifier.focusHalo(focused: Boolean, accent: Color, radius: Dp = 14.dp, spread: Dp = 4.dp): Modifier =
    if (!focused) {
        this
    } else {
        drawBehind {
            val s = spread.toPx()
            val r = radius.toPx() + s
            drawRoundRect(
                color = accent.copy(alpha = Glass.HALO),
                topLeft = Offset(-s, -s),
                size = Size(size.width + 2 * s, size.height + 2 * s),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }

/**
 * Slika koja se pretapa u pozadinu: providnost [opacity] i maske [masks] (crno = vidi se, providno = ne
 * vidi se) primenjuju se na samu sliku, pa iza nje ostaje pozadina ekrana sa prelivom, umesto ravne
 * boje preko slike.
 */
fun Modifier.fadeMask(opacity: Float, vararg masks: Brush): Modifier = this
    .graphicsLayer {
        alpha = opacity
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        masks.forEach { drawRect(brush = it, blendMode = BlendMode.DstIn) }
    }
