package com.bane23.iptvplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.PlayerSettings
import com.bane23.iptvplayer.player.SubtitlePosition
import com.bane23.iptvplayer.player.SubtitleSize
import java.util.Locale

/** Jedno podešavanje sa spiskom vrednosti. Isti opis koriste ekrani za telefon i za TV. */
data class SettingChoice(
    val title: String,
    val summary: String,
    val options: List<String>,
    val selected: Int,
    /** Utiče na izgled titlova, pa se uz njega prikazuje probni titl. */
    val subtitleLook: Boolean = false,
    /** Podešavanje uključeno/isključeno: prikazuje se kao prekidač. Prva vrednost znači uključeno. */
    val toggle: Boolean = false,
    val onSelect: (Int) -> Unit,
) {
    val value: String get() = options.getOrElse(selected) { "" }

    /** Za [toggle]: da li je uključeno. */
    val checked: Boolean get() = selected == 0

    /** Za [toggle]: prelazi u drugo stanje. */
    fun flip() = onSelect(if (checked) 1 else 0)

    /** Prelazi na sledeću vrednost, ukrug. Tako se podešavanje menja jednim dodirom ili tasterom OK. */
    fun cycle() {
        if (options.isNotEmpty()) onSelect((selected + 1) % options.size)
    }
}

/** Jezici koji se nude za zvuk i titlove (ISO 639-1). Nazivi se prikazuju na jeziku uređaja. */
private val LANGUAGE_CODES = listOf(
    "sr", "hr", "bs", "sl", "mk", "en", "de", "fr", "it", "es", "pt", "ru", "tr", "el", "hu", "ro", "bg", "sq", "pl",
    "nl", "sv", "ar",
)

private fun languageName(code: String): String {
    val locale = Locale.getDefault()
    return Locale.forLanguageTag(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Podešavanja plejera kao spisak izbora. */
@Composable
fun playerSettingChoices(settings: PlayerSettings, viewModel: SettingsViewModel): List<SettingChoice> {
    val languageNames = LANGUAGE_CODES.map(::languageName)

    /** Jezik koji nije u spisku (npr. zapamćen iz plejera) dodaje se na kraj, da izbor ne nestane. */
    fun languageChoice(title: String, summary: String, first: String, current: String, set: (String) -> Unit): SettingChoice {
        val extra = current.isNotEmpty() && current !in LANGUAGE_CODES
        val codes = listOf("") + LANGUAGE_CODES + if (extra) listOf(current) else emptyList()
        val names = listOf(first) + languageNames + if (extra) listOf(languageName(current)) else emptyList()
        return SettingChoice(title, summary, names, codes.indexOf(current).coerceAtLeast(0), onSelect = { set(codes[it]) })
    }

    return listOf(
        SettingChoice(
            title = stringResource(R.string.settings_buffer),
            summary = stringResource(R.string.settings_buffer_summary),
            options = listOf(
                stringResource(R.string.buffer_small),
                stringResource(R.string.buffer_normal),
                stringResource(R.string.buffer_large),
            ),
            selected = BufferSize.entries.indexOf(settings.buffer),
            onSelect = { viewModel.setBuffer(BufferSize.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_decoder),
            summary = stringResource(R.string.settings_decoder_summary),
            options = listOf(stringResource(R.string.decoder_auto), stringResource(R.string.decoder_software)),
            selected = DecoderMode.entries.indexOf(settings.decoder),
            onSelect = { viewModel.setDecoder(DecoderMode.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_seek_step),
            summary = stringResource(R.string.settings_seek_step_summary),
            options = PlayerSettings.SEEK_STEP_OPTIONS.map { stringResource(R.string.seek_step_seconds, it) },
            selected = PlayerSettings.SEEK_STEP_OPTIONS.indexOf(settings.seekStepSeconds).coerceAtLeast(0),
            onSelect = { viewModel.setSeekStep(PlayerSettings.SEEK_STEP_OPTIONS[it]) },
        ),
        languageChoice(
            title = stringResource(R.string.settings_audio_language),
            summary = stringResource(R.string.settings_audio_language_summary),
            first = stringResource(R.string.language_default),
            current = settings.audioLanguage,
            set = viewModel::setAudioLanguage,
        ),
        languageChoice(
            title = stringResource(R.string.settings_subtitle_language),
            summary = stringResource(R.string.settings_subtitle_language_summary),
            first = stringResource(R.string.player_subtitles_off),
            current = settings.subtitleLanguage,
            set = viewModel::setSubtitleLanguage,
        ),
        SettingChoice(
            title = stringResource(R.string.settings_subtitle_size),
            summary = stringResource(R.string.settings_subtitle_look_summary),
            options = listOf(
                stringResource(R.string.subtitle_size_small),
                stringResource(R.string.subtitle_size_normal),
                stringResource(R.string.subtitle_size_large),
                stringResource(R.string.subtitle_size_xlarge),
            ),
            selected = SubtitleSize.entries.indexOf(settings.subtitleSize),
            subtitleLook = true,
            onSelect = { viewModel.setSubtitleSize(SubtitleSize.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_subtitle_position),
            summary = stringResource(R.string.settings_subtitle_look_summary),
            options = listOf(
                stringResource(R.string.subtitle_position_low),
                stringResource(R.string.subtitle_position_middle),
                stringResource(R.string.subtitle_position_high),
            ),
            selected = SubtitlePosition.entries.indexOf(settings.subtitlePosition),
            subtitleLook = true,
            onSelect = { viewModel.setSubtitlePosition(SubtitlePosition.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_subtitle_background),
            summary = stringResource(R.string.settings_subtitle_look_summary),
            options = listOf(stringResource(R.string.setting_on), stringResource(R.string.setting_off)),
            selected = if (settings.subtitleBackground) 0 else 1,
            subtitleLook = true,
            toggle = true,
            onSelect = { viewModel.setSubtitleBackground(it == 0) },
        ),
    )
}

/** Naziv jezika aplikacije u podešavanjima. Srpski i engleski se uvek pišu na svom jeziku. */
@Composable
fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.SERBIAN -> R.string.settings_language_serbian
        AppLanguage.ENGLISH -> R.string.settings_language_english
    },
)
