package com.bane23.iptvplayer.ui.common.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.SubtitlePosition
import com.bane23.iptvplayer.player.SubtitleSize
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import java.text.DecimalFormat

/** Korak pomeranja titla. */
const val SUBTITLE_SHIFT_STEP_MS = 500L

/** Najveće pomeranje titla u oba smera. */
const val MAX_SUBTITLE_SHIFT_MS = 30_000L

/**
 * Red sa jednom vrednošću koja ide korak naniže ili naviše: dugmad − i + na telefonu, strelice levo
 * i desno na TV-u. [onStep] dobija -1 ili 1.
 */
data class StepChoice(
    val title: String,
    val value: String,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
    val onStep: (Int) -> Unit,
)

/**
 * Redovi na dnu menija „Titlovi” u plejeru filmova i epizoda: veličina, položaj i pozadina titla
 * (ista podešavanja kao u Podešavanjima) i pomeranje titla.
 * [subtitleOffsetMs] je trenutno pomeranje; null kad titl ne može da se pomera (ugrađeni titlovi),
 * i tada tog reda nema.
 */
@Composable
fun subtitleStepChoices(
    subtitleOffsetMs: Long?,
    onShiftSubtitle: (Long) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
): List<StepChoice> {
    val settings by settingsViewModel.playerSettings.collectAsStateWithLifecycle()
    val sizes = SubtitleSize.entries
    val sizeLabels = listOf(
        stringResource(R.string.subtitle_size_small),
        stringResource(R.string.subtitle_size_normal),
        stringResource(R.string.subtitle_size_large),
        stringResource(R.string.subtitle_size_xlarge),
    )
    val sizeIndex = sizes.indexOf(settings.subtitleSize)
    val size = StepChoice(
        title = stringResource(R.string.subtitle_step_size),
        value = sizeLabels.getOrElse(sizeIndex) { "" },
        canDecrease = sizeIndex > 0,
        canIncrease = sizeIndex < sizes.lastIndex,
        onStep = { delta ->
            val next = (sizeIndex + delta).coerceIn(0, sizes.lastIndex)
            if (next != sizeIndex) settingsViewModel.setSubtitleSize(sizes[next])
        },
    )
    val positions = SubtitlePosition.entries
    val positionLabels = listOf(
        stringResource(R.string.subtitle_position_low),
        stringResource(R.string.subtitle_position_middle),
        stringResource(R.string.subtitle_position_high),
    )
    val positionIndex = positions.indexOf(settings.subtitlePosition)
    val position = StepChoice(
        title = stringResource(R.string.subtitle_step_position),
        value = positionLabels.getOrElse(positionIndex) { "" },
        canDecrease = positionIndex > 0,
        canIncrease = positionIndex < positions.lastIndex,
        onStep = { delta ->
            val next = (positionIndex + delta).coerceIn(0, positions.lastIndex)
            if (next != positionIndex) settingsViewModel.setSubtitlePosition(positions[next])
        },
    )
    // Isključena je levo, uključena desno.
    val backgroundOn = settings.subtitleBackground
    val background = StepChoice(
        title = stringResource(R.string.subtitle_step_background),
        value = stringResource(if (backgroundOn) R.string.setting_on else R.string.setting_off),
        canDecrease = backgroundOn,
        canIncrease = !backgroundOn,
        onStep = { delta ->
            val wanted = delta > 0
            if (wanted != backgroundOn) settingsViewModel.setSubtitleBackground(wanted)
        },
    )
    val shift = subtitleOffsetMs?.let { offset ->
        StepChoice(
            title = stringResource(R.string.subtitle_step_shift),
            value = stringResource(R.string.subtitle_shift_value, shiftLabel(offset)),
            canDecrease = offset > -MAX_SUBTITLE_SHIFT_MS,
            canIncrease = offset < MAX_SUBTITLE_SHIFT_MS,
            onStep = { delta -> onShiftSubtitle(delta * SUBTITLE_SHIFT_STEP_MS) },
        )
    }
    return listOfNotNull(size, position, background, shift)
}

/** Pomeranje kao broj sekundi sa znakom, npr. „+0,5”; nula je bez znaka. */
internal fun shiftLabel(offsetMs: Long): String =
    DecimalFormat(if (offsetMs == 0L) "0.0" else "+0.0;-0.0").format(offsetMs / 1000.0)
