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
 * a TV usklađivanje osvežavanja ekrana. [speed] je brzina puštanja filma ili epizode; null za kanal uživo, gde
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
    val brightnessSlider by settingsViewModel.playerBrightnessSwipe.collectAsStateWithLifecycle()
    val volumeSlider by settingsViewModel.playerVolumeSwipe.collectAsStateWithLifecycle()
    val backgroundPlay by settingsViewModel.backgroundPlay.collectAsStateWithLifecycle()
    val sleepMinutes by settingsViewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val matchFrameRate by settingsViewModel.matchFrameRate.collectAsStateWithLifecycle()
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
        options = listOf(
            stringResource(R.string.decoder_auto),
            stringResource(R.string.decoder_software),
            stringResource(R.string.decoder_tunnel),
        ),
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
    val aspectChoice = SettingChoice(
        title = stringResource(R.string.player_aspect),
        summary = "",
        options = AspectMode.entries.map { it.label() },
        selected = AspectMode.entries.indexOf(aspectMode),
        onSelect = { onAspectMode(AspectMode.entries[it]) },
    )

    val bufferChoice = SettingChoice(
        title = stringResource(R.string.settings_buffer),
        // Jedina stavka koja ne važi odmah, nego od sledećeg puštanja.
        summary = stringResource(R.string.player_buffer_note),
        options = listOf(
            stringResource(R.string.buffer_small),
            stringResource(R.string.buffer_normal),
            stringResource(R.string.buffer_large),
        ),
        selected = BufferSize.entries.indexOf(settings.buffer),
        onSelect = { settingsViewModel.setBuffer(BufferSize.entries[it]) },
    )
    // Isti redosled na telefonu i na TV-u. Veličina i pomeranje titla su u meniju „Titlovi”.
    val shared = listOfNotNull(
        aspectChoice,
        decoderChoice,
        sleepChoice,
        speedChoice,
        bufferChoice,
    )
    // Samo TV: usklađivanje osvežavanja ekrana sa snimkom. Ista sačuvana vrednost kao u glavnim
    // Podešavanjima, pa važi poslednja izmena, gde god da je napravljena.
    if (tv) {
        return shared + switch(
            stringResource(R.string.settings_match_frame_rate),
            matchFrameRate,
            settingsViewModel::setMatchFrameRate,
        )
    }
    // Samo telefon: trake na dodir i puštanje u pozadini.
    return shared + listOf(
        switch(stringResource(R.string.settings_brightness_swipe), brightnessSlider, settingsViewModel::setPlayerBrightnessSwipe),
        switch(stringResource(R.string.settings_volume_swipe), volumeSlider, settingsViewModel::setPlayerVolumeSwipe),
        switch(stringResource(R.string.settings_background_play), backgroundPlay, settingsViewModel::setBackgroundPlay),
    )
}
