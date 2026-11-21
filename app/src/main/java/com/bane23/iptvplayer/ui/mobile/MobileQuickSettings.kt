package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.StepChoice

/** Širina oblačića sa spiskom zapisa i sa podešavanjima (vrednosti su duže, pa je širi). */
internal val MobileTrackMenuWidth = 260.dp
internal val MobileSettingsMenuWidth = 300.dp

/**
 * Stavke menija „Podešavanja” u plejeru; sadržaj oblačića iznad dugmeta (PlayerMenuBubble).
 * Prekidači deluju odmah, a stavke sa više vrednosti na dodir prelaze na sledeću. Meni se zatvara
 * dodirom pored njega ili tasterom Nazad (to rešava ekran plejera).
 */
@Composable
internal fun MobileQuickSettingsMenu(choices: List<SettingChoice>) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
 * Spisak iz kog se bira jedna stavka (titlovi, zvuk, izvori, trajanje snimanja); sadržaj oblačića
 * iznad dugmeta (PlayerMenuBubble). Dodir bira; zatvaranje rešava ekran plejera. Po želji ima naslov
 * [title], poruku [emptyText] kad nema stavki, stavku [extra] koja otvara nešto drugo (npr. „Potraži
 * online”), a ispod redove [steps] sa dugmadima − i + (veličina i pomeranje titla).
 */
@Composable
internal fun MobileChoiceMenu(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    title: String? = null,
    emptyText: String? = null,
    extra: Pair<String, () -> Unit>? = null,
    steps: List<StepChoice> = emptyList(),
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (options.isEmpty() && emptyText != null) {
            Text(
                text = emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
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

/** Red spiska: kvačica ispred izabranog, strelica iza stavke koja otvara nešto drugo. */
@Composable
private fun SubtitleRow(label: String, leading: ImageVector?, trailing: ImageVector?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
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
            .heightIn(min = 44.dp)
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
            .heightIn(min = 44.dp)
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
