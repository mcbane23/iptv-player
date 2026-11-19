package com.bane23.iptvplayer.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.PlayerSettings
import com.bane23.iptvplayer.player.SubtitlePosition
import com.bane23.iptvplayer.player.SubtitleSize
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import java.util.Locale

/**
 * Grupe u Podešavanjima, redom kojim se prikazuju: najčešće korišćene su na vrhu. Iste su na
 * telefonu i na TV-u; grupa bez ijedne stavke na uređaju se ne prikazuje.
 */
enum class SettingsGroup(@StringRes val title: Int, @StringRes val summary: Int, val icon: ImageVector) {
    CONTENT(R.string.settings_group_content, R.string.settings_group_content_summary, AppIcons.Playlists),
    WATCHING(R.string.settings_group_watching, R.string.settings_group_watching_summary, AppIcons.Play),
    SUBTITLES(R.string.settings_group_subtitles, R.string.settings_group_subtitles_summary, AppIcons.Subtitles),
    LOOK(R.string.settings_group_look, R.string.settings_group_look_summary, AppIcons.Brightness),
    RECORDINGS(R.string.settings_group_recordings, R.string.settings_group_recordings_summary, AppIcons.Record),
    ACCOUNTS(R.string.settings_group_accounts, R.string.settings_group_accounts_summary, AppIcons.Cast),
    PICTURE(R.string.settings_group_picture, R.string.settings_group_picture_summary, AppIcons.LiveTv),
    ABOUT(R.string.settings_about_header, R.string.settings_group_about_summary, AppIcons.Info),
}

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
    /** Grupa u Podešavanjima u kojoj se podešavanje prikazuje; null za izbore van Podešavanja (plejer). */
    val group: SettingsGroup? = null,
    /**
     * Mesta po redu važnosti (npr. prvi, drugi i treći jezik): podešavanje je tada jedan red koji
     * otvara ova mesta, a svako mesto je obično podešavanje sa spiskom vrednosti.
     */
    val slots: List<SettingChoice> = emptyList(),
    val onSelect: (Int) -> Unit = {},
) {
    /** Trenutna vrednost; za podešavanje sa mestima izabrane vrednosti redom (prva vrednost mesta znači „nije izabrano”). */
    val value: String
        get() = if (slots.isEmpty()) {
            options.getOrElse(selected) { "" }
        } else {
            slots.filter { it.selected > 0 }.map { it.value }.distinct().joinToString(", ")
                .ifEmpty { slots.first().options.firstOrNull().orEmpty() }
        }

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

    val notChosen = stringResource(R.string.language_not_chosen)

    /** Jezik koji nije u spisku (npr. prenet sa drugog uređaja) dodaje se na kraj, da izbor ne nestane. */
    fun languageChoice(title: String, summary: String, current: String, set: (String) -> Unit): SettingChoice {
        val extra = current.isNotEmpty() && current !in LANGUAGE_CODES
        val codes = listOf("") + LANGUAGE_CODES + if (extra) listOf(current) else emptyList()
        val names = listOf(notChosen) + languageNames + if (extra) listOf(languageName(current)) else emptyList()
        return SettingChoice(
            title, summary, names, codes.indexOf(current).coerceAtLeast(0),
            group = SettingsGroup.SUBTITLES,
            onSelect = { set(codes[it]) },
        )
    }

    val subtitleLanguageTitles = listOf(
        stringResource(R.string.settings_subtitle_language_1),
        stringResource(R.string.settings_subtitle_language_2),
        stringResource(R.string.settings_subtitle_language_3),
    )
    val audioLanguageTitles = listOf(
        stringResource(R.string.settings_audio_language_1),
        stringResource(R.string.settings_audio_language_2),
        stringResource(R.string.settings_audio_language_3),
    )

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
            group = SettingsGroup.PICTURE,
            onSelect = { viewModel.setBuffer(BufferSize.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_decoder),
            summary = stringResource(R.string.settings_decoder_summary),
            options = listOf(
                stringResource(R.string.decoder_auto),
                stringResource(R.string.decoder_software),
                stringResource(R.string.decoder_tunnel),
            ),
            selected = DecoderMode.entries.indexOf(settings.decoder),
            group = SettingsGroup.PICTURE,
            onSelect = { viewModel.setDecoder(DecoderMode.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_seek_step),
            summary = stringResource(R.string.settings_seek_step_summary),
            options = PlayerSettings.SEEK_STEP_OPTIONS.map { stringResource(R.string.seek_step_seconds, it) },
            selected = PlayerSettings.SEEK_STEP_OPTIONS.indexOf(settings.seekStepSeconds).coerceAtLeast(0),
            group = SettingsGroup.WATCHING,
            onSelect = { viewModel.setSeekStep(PlayerSettings.SEEK_STEP_OPTIONS[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_subtitle_languages),
            summary = stringResource(R.string.settings_subtitle_language_summary),
            options = emptyList(),
            selected = 0,
            group = SettingsGroup.SUBTITLES,
            slots = subtitleLanguageTitles.mapIndexed { slot, title ->
                languageChoice(
                    title = title,
                    summary = stringResource(R.string.settings_subtitle_language_summary),
                    current = settings.subtitleLanguageSlots.getOrElse(slot) { "" },
                    set = { viewModel.setSubtitleLanguage(slot, it) },
                )
            },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_audio_languages),
            summary = stringResource(R.string.settings_audio_language_summary),
            options = emptyList(),
            selected = 0,
            group = SettingsGroup.SUBTITLES,
            slots = audioLanguageTitles.mapIndexed { slot, title ->
                languageChoice(
                    title = title,
                    summary = stringResource(R.string.settings_audio_language_summary),
                    current = settings.audioLanguageSlots.getOrElse(slot) { "" },
                    set = { viewModel.setAudioLanguage(slot, it) },
                )
            },
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
            group = SettingsGroup.SUBTITLES,
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
            group = SettingsGroup.SUBTITLES,
            subtitleLook = true,
            onSelect = { viewModel.setSubtitlePosition(SubtitlePosition.entries[it]) },
        ),
        SettingChoice(
            title = stringResource(R.string.settings_subtitle_background),
            summary = stringResource(R.string.settings_subtitle_look_summary),
            options = listOf(stringResource(R.string.setting_on), stringResource(R.string.setting_off)),
            selected = if (settings.subtitleBackground) 0 else 1,
            group = SettingsGroup.SUBTITLES,
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

/**
 * Objašnjenje uz izbor jezika: nabraja dostupne jezike, svaki na svom jeziku. Spisak se pravi iz
 * [AppLanguage], pa novi jezik sam ulazi u objašnjenje.
 */
@Composable
fun languageSummary(): String {
    val names = AppLanguage.entries.filter { it.tag != null }.map { it.label() }
    return stringResource(R.string.settings_language_summary, names.joinToString(", "))
}
