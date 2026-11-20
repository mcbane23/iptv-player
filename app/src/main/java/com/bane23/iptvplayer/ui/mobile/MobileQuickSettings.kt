package com.bane23.iptvplayer.ui.mobile

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.StepChoice

/**
 * Panel „Brza podešavanja” preko slike: prekidači deluju odmah, a stavke sa više vrednosti na dodir
 * prelaze na sledeću. U položenom telefonu stoji uz desnu ivicu, u uspravnom pri dnu. Zatvara se
 * dodirom van panela ili tasterom Nazad (to rešava ekran plejera).
 */
@Composable
internal fun BoxScope.MobileQuickSettingsPanel(choices: List<SettingChoice>) {
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    Column(
        modifier = Modifier
            .align(if (portrait) Alignment.BottomCenter else Alignment.CenterEnd)
            .safeDrawingPadding()
            .padding(12.dp)
            .then(if (portrait) Modifier.fillMaxWidth() else Modifier.widthIn(max = 320.dp))
            .clip(PanelShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            // Dodir na sam panel ne sme da ga zatvori.
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.player_quick_settings),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        choices.forEach { choice -> QuickSettingRow(choice) }
        // Napomene uz stavke koje ne važe odmah (bafer).
        choices.map { it.summary }.filter { it.isNotEmpty() }.forEach { note ->
            Text(
                text = note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Meni „Titlovi” preko slike, na istom mestu i istog izgleda kao „Brza podešavanja”: spisak titlova
 * (dodir bira i zatvara, to rešava ekran plejera), po želji stavka [extra] (npr. „Potraži online”),
 * a ispod redovi [steps] sa dugmadima − i +. Kontrole plejera su za to vreme sklonjene, pa se titl
 * vidi dok se podešava.
 */
@Composable
internal fun BoxScope.MobileSubtitlesPanel(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    extra: Pair<String, () -> Unit>?,
    steps: List<StepChoice>,
) {
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    Column(
        modifier = Modifier
            .align(if (portrait) Alignment.BottomCenter else Alignment.CenterEnd)
            .safeDrawingPadding()
            .padding(12.dp)
            .then(if (portrait) Modifier.fillMaxWidth() else Modifier.widthIn(max = 320.dp))
            .clip(PanelShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            // Dodir na sam panel ne sme da ga zatvori.
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.player_subtitles),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        options.forEachIndexed { index, (label, selected) ->
            SubtitleRow(
                label = label,
                leading = if (selected) AppIcons.Check else null,
                trailing = null,
                onClick = { onSelect(index) },
            )
        }
        if (extra != null) {
            SubtitleRow(label = extra.first, leading = null, trailing = AppIcons.ChevronRight, onClick = extra.second)
        }
        if (steps.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            steps.forEach { step -> StepRow(step) }
        }
    }
}

/** Red spiska titlova: kvačica ispred izabranog, strelica iza stavke koja otvara nešto drugo. */
@Composable
private fun SubtitleRow(label: String, leading: ImageVector?, trailing: ImageVector?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        // Mesto za kvačicu postoji u svakom redu, da nazivi stoje u istoj liniji.
        Box(modifier = Modifier.size(20.dp)) {
            if (leading != null) Icon(leading, contentDescription = null, tint = scheme.primary)
        }
        Text(
            text = label,
            color = if (leading != null) scheme.primary else scheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Red sa jednom vrednošću i dugmadima − i +. */
@Composable
private fun StepRow(step: StepChoice) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = step.title,
            color = scheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { step.onStep(-1) }, enabled = step.canDecrease) {
            Icon(AppIcons.Remove, contentDescription = stringResource(R.string.step_decrease, step.title))
        }
        Text(
            text = step.value,
            color = scheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 76.dp),
        )
        IconButton(onClick = { step.onStep(1) }, enabled = step.canIncrease) {
            Icon(AppIcons.Add, contentDescription = stringResource(R.string.step_increase, step.title))
        }
    }
}

@Composable
private fun QuickSettingRow(choice: SettingChoice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (choice.toggle) {
                    Modifier.toggleable(value = choice.checked, role = Role.Switch, onValueChange = { choice.flip() })
                } else {
                    Modifier.clickable(role = Role.Button, onClick = choice::cycle)
                },
            )
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = choice.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (choice.toggle) {
            Switch(checked = choice.checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
        } else {
            Text(
                text = choice.value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private val PanelShape = RoundedCornerShape(18.dp)
