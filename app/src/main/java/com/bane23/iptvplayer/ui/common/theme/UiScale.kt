package com.bane23.iptvplayer.ui.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Veličina prikaza koju korisnik bira u Podešavanjima. Umanjuje ceo interfejs (tekst, ikonice i
 * razmake zajedno), pa na ekran staje više redova i duži nazivi. U podešavanjima se čuva ime
 * vrednosti, zato se postojeća imena ne menjaju.
 */
enum class UiScale(val factor: Float) {
    P100(1f),
    P85(0.85f),
    P75(0.75f),
    P65(0.65f),
    ;

    /** Naziv za prikaz, npr. „75%”. */
    val label: String get() = "${Math.round(factor * 100)}%"

    companion object {
        /** Na TV-u se gleda izdaleka sa velike slike, pa je početni prikaz sitniji nego na telefonu. */
        fun default(isTv: Boolean): UiScale = if (isTv) P75 else P100

        /** Ponuđene vrednosti. Na telefonu nema najsitnije, da dugmad ostanu dovoljno velika za prst. */
        fun choices(isTv: Boolean): List<UiScale> = if (isTv) entries else entries - P65

        /** Vrednost po sačuvanom imenu; nepoznato ime ili null daje podrazumevanu za uređaj. */
        fun fromName(name: String?, isTv: Boolean): UiScale =
            choices(isTv).firstOrNull { it.name == name } ?: default(isTv)

        /** Sledeća ponuđena vrednost posle [current], ukrug. */
        fun next(current: UiScale, isTv: Boolean): UiScale {
            val all = choices(isTv)
            return all[(all.indexOf(current) + 1) % all.size]
        }
    }
}

/**
 * Crta [content] umanjen na [scale]. Veličina fonta iz sistemskih podešavanja se i dalje poštuje.
 *
 * Sadržaj se uvek crta na istom mestu, i za 100%. Kad bi se za 100% crtao mimo
 * `CompositionLocalProvider`, promena između 100% i neke druge vrednosti bi srušila i ponovo
 * napravila ceo interfejs, pa bi se navigacija vratila na početni ekran.
 */
@Composable
fun ScaledUi(scale: UiScale, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = remember(density, scale) {
        if (scale == UiScale.P100) density else Density(density.density * scale.factor, density.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}
