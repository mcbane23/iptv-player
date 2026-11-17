package com.bane23.iptvplayer.ui.common.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.SleepTimer
import com.bane23.iptvplayer.player.SubtitleSize
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import java.text.DecimalFormat

/** Brzine puštanja koje se nude za filmove i epizode. */
val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Brzina kao tekst, npr. „1,25×”. */
fun speedLabel(speed: Float): String = DecimalFormat("0.##").format(speed.toDouble()) + "×"

/**
 * Stavke panela „Podešavanja” u plejeru: nekoliko podešavanja koja se menjaju dok se gleda, bez
 * izlaska iz plejera. Telefon ([tv] = false) dobija prekidače za bočne trake i puštanje u pozadini,
 * a TV odnos slike i bafer. [speed] je brzina puštanja filma ili epizode; null za kanal uživo, gde
 * brzina nema smisla.
 */
@Composable
fun quickSettingChoices(
    tv: Boolean,
    aspectMode: AspectMode,
    onAspectMode: (AspectMode) -> Unit,
    decoder: DecoderMode,
    onDecoder: (DecoderMode) -> Unit,
    speed: Float? = null,
    onSpeed: (Float) -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
): List<SettingChoice> {
    val settings by settingsViewModel.playerSettings.collectAsStateWithLifecycle()
    val brightnessSlider by settingsViewModel.playerBrightnessSlider.collectAsStateWithLifecycle()
    val volumeSlider by settingsViewModel.playerVolumeSlider.collectAsStateWithLifecycle()
    val backgroundPlay by settingsViewModel.backgroundPlay.collectAsStateWithLifecycle()
    val sleepMinutes by settingsViewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val onOff = listOf(stringResource(R.string.setting_on), stringResource(R.string.setting_off))

    fun switch(title: String, checked: Boolean, set: (Boolean) -> Unit) = SettingChoice(
        title = title,
        summary = "",
        options = onOff,
        selected = if (checked) 0 else 1,
        toggle = true,
        onSelect = { set(it == 0) },
    )

    val decoderChoice = SettingChoice(
        title = stringResource(R.string.settings_decoder),
        summary = "",
        options = listOf(stringResource(R.string.decoder_auto), stringResource(R.string.decoder_software)),
        selected = DecoderMode.entries.indexOf(decoder),
        onSelect = { onDecoder(DecoderMode.entries[it]) },
    )
    val sleepChoice = SettingChoice(
        title = stringResource(R.string.sleep_timer),
        summary = "",
        options = SleepTimer.OPTIONS.map {
            if (it == 0) stringResource(R.string.sleep_timer_off) else stringResource(R.string.sleep_timer_minutes, it)
        },
        selected = SleepTimer.OPTIONS.indexOf(sleepMinutes).coerceAtLeast(0),
        onSelect = { settingsViewModel.setSleepTimer(SleepTimer.OPTIONS[it]) },
    )
    val speedChoice = speed?.let { current ->
        SettingChoice(
            title = stringResource(R.string.player_speed),
            summary = "",
            options = PLAYBACK_SPEEDS.map(::speedLabel),
            selected = PLAYBACK_SPEEDS.indexOf(current).coerceAtLeast(0),
            onSelect = { onSpeed(PLAYBACK_SPEEDS[it]) },
        )
    }
    val subtitleSizeChoice = SettingChoice(
        title = stringResource(R.string.settings_subtitle_size),
        summary = "",
        options = listOf(
            stringResource(R.string.subtitle_size_small),
            stringResource(R.string.subtitle_size_normal),
            stringResource(R.string.subtitle_size_large),
            stringResource(R.string.subtitle_size_xlarge),
        ),
        selected = SubtitleSize.entries.indexOf(settings.subtitleSize),
        onSelect = { settingsViewModel.setSubtitleSize(SubtitleSize.entries[it]) },
    )

    if (!tv) {
        return listOfNotNull(
            switch(stringResource(R.string.settings_brightness_slider), brightnessSlider, settingsViewModel::setPlayerBrightnessSlider),
            switch(stringResource(R.string.settings_volume_slider), volumeSlider, settingsViewModel::setPlayerVolumeSlider),
            switch(stringResource(R.string.settings_background_play), backgroundPlay, settingsViewModel::setBackgroundPlay),
            decoderChoice,
            sleepChoice,
            speedChoice,
            // Plejer kanala nema titlove, pa veličina stoji samo uz filmove i epizode.
            subtitleSizeChoice.takeIf { speed != null },
        )
    }
    return listOfNotNull(
        SettingChoice(
            title = stringResource(R.string.player_aspect),
            summary = "",
            options = AspectMode.entries.map { it.label() },
            selected = AspectMode.entries.indexOf(aspectMode),
            onSelect = { onAspectMode(AspectMode.entries[it]) },
        ),
        decoderChoice,
        subtitleSizeChoice.takeIf { speed != null },
        sleepChoice,
        speedChoice,
        // Jedina stavka koja ne važi odmah, nego od sledećeg puštanja.
        SettingChoice(
            title = stringResource(R.string.settings_buffer),
            summary = stringResource(R.string.player_buffer_note),
            options = listOf(
                stringResource(R.string.buffer_small),
                stringResource(R.string.buffer_normal),
                stringResource(R.string.buffer_large),
            ),
            selected = BufferSize.entries.indexOf(settings.buffer),
            onSelect = { settingsViewModel.setBuffer(BufferSize.entries[it]) },
        ),
    )
}
