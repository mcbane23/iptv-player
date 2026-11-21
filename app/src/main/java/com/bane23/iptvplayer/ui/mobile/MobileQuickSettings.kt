package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.PlayerMenuDivider
import com.bane23.iptvplayer.ui.common.player.PlayerMenuMuted
import com.bane23.iptvplayer.ui.common.player.PlayerMenuNote
import com.bane23.iptvplayer.ui.common.player.PlayerMenuText
import com.bane23.iptvplayer.ui.common.player.StepChoice

/** Širina oblačića sa spiskom zapisa i sa podešavanjima (vrednosti su duže, pa je širi). */
internal val MobileTrackMenuWidth = 230.dp
internal val MobileSettingsMenuWidth = 270.dp

/** Visina reda u meniju plejera: niža od običnih spiskova, ali i dalje laka za dodir. */
internal val MobileMenuRowHeight = 36.dp

/**
 * Stavke menija „Podešavanja” u plejeru; sadržaj oblačića iznad trake (PlayerMenuBubble).
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
                color = PlayerMenuMuted,
                style = PlayerMenuNote,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Spisak iz kog se bira jedna stavka (titlovi, zvuk, izvori, trajanje snimanja); sadržaj oblačića
 * iznad trake (PlayerMenuBubble). Dodir bira; zatvaranje rešava ekran plejera. Po želji ima naslov
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
                color = PlayerMenuMuted,
                style = PlayerMenuText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        if (options.isEmpty() && emptyText != null) {
            Text(
                text = emptyText,
                color = PlayerMenuMuted,
                style = PlayerMenuText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
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
            HorizontalDivider(color = PlayerMenuDivider, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            steps.forEach { step -> StepRow(step) }
        }
    }
}

/** Red spiska: kvačica ispred izabranog, strelica iza stavke koja otvara nešto drugo. */
@Composable
private fun SubtitleRow(label: String, leading: ImageVector?, trailing: ImageVector?, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MobileMenuRowHeight)
            .clip(MenuRowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        // Mesto za kvačicu postoji u svakom redu, da nazivi stoje u istoj liniji.
        Box(modifier = Modifier.size(14.dp)) {
            if (leading != null) Icon(leading, contentDescription = null, tint = primary)
        }
        Text(
            text = label,
            color = if (leading != null) primary else Color.White,
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = PlayerMenuMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** Red sa jednom vrednošću i dugmadima − i +. */
@Composable
private fun StepRow(step: StepChoice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MobileMenuRowHeight)
            .padding(start = 8.dp, end = 4.dp),
    ) {
        Text(
            text = step.title,
            color = Color.White,
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StepButton(AppIcons.Remove, stringResource(R.string.step_decrease, step.title), step.canDecrease) { step.onStep(-1) }
        Text(
            text = step.value,
            color = MaterialTheme.colorScheme.primary,
            style = PlayerMenuText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 60.dp),
        )
        StepButton(AppIcons.Add, stringResource(R.string.step_increase, step.title), step.canIncrease) { step.onStep(1) }
    }
}

/** Okruglo dugme − ili +: beli znak na svetlijem krugu; bledo kad dalje ne može. */
@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    // Dodir hvata ceo kvadrat visine reda, a krug je manji.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(MobileMenuRowHeight)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f), CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun QuickSettingRow(choice: SettingChoice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MobileMenuRowHeight)
            .clip(MenuRowShape)
            .then(
                if (choice.toggle) {
                    Modifier.toggleable(value = choice.checked, role = Role.Switch, onValueChange = { choice.flip() })
                } else {
                    Modifier.clickable(role = Role.Button, onClick = choice::cycle)
                },
            )
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = choice.title,
            color = Color.White,
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (choice.toggle) {
            // Prekidač je umanjen, da stane u niži red.
            Switch(checked = choice.checked, onCheckedChange = null, modifier = Modifier.scale(0.7f))
        } else {
            Text(
                text = choice.value,
                color = MaterialTheme.colorScheme.primary,
                style = PlayerMenuText,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private val MenuRowShape = RoundedCornerShape(7.dp)
