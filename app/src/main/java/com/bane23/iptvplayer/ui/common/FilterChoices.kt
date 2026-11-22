package com.bane23.iptvplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodFilter
import com.bane23.iptvplayer.data.repo.VodYears
import com.bane23.iptvplayer.data.text.SearchText
import com.bane23.iptvplayer.domain.ChannelFilter
import com.bane23.iptvplayer.domain.ChannelRules
import java.text.Collator
import java.util.Locale

/** Natpisi i izbori filtera; iste koriste ekrani za telefon i za TV. */

@Composable
private fun VodYears.label(): String = stringResource(
    when (this) {
        VodYears.ALL -> R.string.filter_all
        VodYears.FROM_2020 -> R.string.filter_years_2020
        VodYears.FROM_2010 -> R.string.filter_years_2010
        VodYears.FROM_2000 -> R.string.filter_years_2000
        VodYears.OLDER -> R.string.filter_years_older
    },
)

@Composable
private fun ratingLabel(rating: Int): String =
    if (rating == 0) stringResource(R.string.filter_all) else stringResource(R.string.filter_rating_value, rating)

@Composable
private fun qualityLabel(quality: Int): String = stringResource(
    when (quality) {
        1 -> R.string.filter_quality_hd
        2 -> R.string.filter_quality_fhd
        3 -> R.string.filter_quality_4k
        else -> R.string.filter_all_channels
    },
)

/** Kratak opis uključenog filtera („2020. i novije, 7+, 4K”); prazan kad filtera nema. */
@Composable
fun VodFilter.summary(): String = listOfNotNull(
    years.takeIf { it != VodYears.ALL }?.label(),
    minRating.takeIf { it > 0 }?.let { ratingLabel(it) },
    if (only4k) stringResource(R.string.filter_4k_short) else null,
).joinToString(", ")

@Composable
fun ChannelFilter.summary(): String {
    val locale = LocalConfiguration.current.locales[0]
    return listOfNotNull(
        minQuality.takeIf { it > 0 }?.let { qualityLabel(it) },
        ChannelRules.countryCode(country)?.let { Locale("", it).getDisplayCountry(locale) },
    ).joinToString(", ")
}

/**
 * Redni brojevi vrednosti koje odgovaraju upitu [query], po redu u spisku. Prva vrednost („Sve”)
 * ostaje uvek. Poredi se bez kvačica i velikih slova, pa „srb” nalazi „Srbija”, a „ceska” „Češka”.
 */
internal fun matchingOptions(options: List<String>, query: String): List<Int> {
    val wanted = SearchText.normalize(query)
    if (wanted.isEmpty()) return options.indices.toList()
    return options.indices.filter { it == 0 || SearchText.normalize(options[it]).contains(wanted) }
}

/** Zemlja za spisak: naziv na jeziku aplikacije i oznaka, npr. „Srbija (RS)”. */
internal fun countryLabel(code: String, locale: Locale): String {
    val name = Locale("", code).getDisplayCountry(locale)
    return if (name.isEmpty() || name == code) code else "$name ($code)"
}

/** Oznake zemalja poređane po nazivu na jeziku [locale], po pravilima tog jezika (č, š, ž na svom mestu). */
internal fun sortedCountries(codes: List<String>, locale: Locale): List<String> {
    val collator = Collator.getInstance(locale)
    return codes.distinct().sortedWith { a, b -> collator.compare(countryLabel(a, locale), countryLabel(b, locale)) }
}

/** Izbori filtera filmova i serija: godina, najmanja ocena i prekidač „Samo 4K”. */
@Composable
fun vodFilterChoices(filter: VodFilter, onChange: (VodFilter) -> Unit): List<SettingChoice> = listOf(
    SettingChoice(
        title = stringResource(R.string.filter_years),
        summary = "",
        options = VodYears.entries.map { it.label() },
        selected = filter.years.ordinal,
        onSelect = { onChange(filter.copy(years = VodYears.entries[it])) },
    ),
    SettingChoice(
        title = stringResource(R.string.filter_rating),
        summary = "",
        options = VodFilter.RATINGS.map { ratingLabel(it) },
        selected = VodFilter.RATINGS.indexOf(filter.minRating).coerceAtLeast(0),
        onSelect = { onChange(filter.copy(minRating = VodFilter.RATINGS[it])) },
    ),
    SettingChoice(
        title = stringResource(R.string.filter_only_4k),
        summary = "",
        options = listOf(stringResource(R.string.filter_on), stringResource(R.string.filter_off)),
        selected = if (filter.only4k) 0 else 1,
        toggle = true,
        onSelect = { onChange(filter.copy(only4k = it == 0)) },
    ),
)

/** Izbori filtera kanala: kvalitet i zemlja. [countries] su oznake koje liste zaista koriste. */
@Composable
fun liveFilterChoices(filter: ChannelFilter, countries: List<String>, onChange: (ChannelFilter) -> Unit): List<SettingChoice> {
    val locale = LocalConfiguration.current.locales[0]
    // Izabrana zemlja ostaje u spisku i kad je posle osvežavanja liste više nema dovoljno.
    val chosen = ChannelRules.countryCode(filter.country)
    val offered = remember(countries, chosen, locale) { sortedCountries(countries + listOfNotNull(chosen), locale) }
    return listOf(
        SettingChoice(
            title = stringResource(R.string.filter_quality),
            summary = "",
            options = (0..3).map { qualityLabel(it) },
            selected = filter.minQuality.coerceIn(0, 3),
            onSelect = { onChange(filter.copy(minQuality = it)) },
        ),
        SettingChoice(
            title = stringResource(R.string.filter_country),
            summary = if (offered.isEmpty()) stringResource(R.string.filter_country_none) else "",
            options = listOf(stringResource(R.string.filter_all)) + offered.map { countryLabel(it, locale) },
            selected = chosen?.let { offered.indexOf(it) + 1 } ?: 0,
            list = true,
            onSelect = { onChange(filter.copy(country = offered.getOrNull(it - 1))) },
        ),
    )
}
