package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/**
 * Dugme filtera u zaglavlju: otvara list odozdo sa izborima. Dok je filter uključen, dugme je u
 * boji teme i nosi broj uključenih uslova. Stanje preživljava okretanje telefona.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterButton(
    title: String,
    count: Int,
    choices: List<SettingChoice>,
    onReset: () -> Unit,
    /** Poziva se pri otvaranju, da se učitaju vrednosti koje zavise od liste. */
    onOpen: () -> Unit = {},
    /** Manje dugme, za red ikonica u levom panelu položenog telefona. */
    compact: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = {
            onOpen()
            open = true
        },
        modifier = if (compact) Modifier.size(36.dp) else Modifier,
    ) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(
                imageVector = AppIcons.Filter,
                contentDescription = title,
                modifier = if (compact) Modifier.size(20.dp) else Modifier,
                tint = if (count > 0) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            for (choice in choices) {
                if (choice.toggle) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(text = choice.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = choice.checked, onCheckedChange = { choice.flip() })
                    }
                } else {
                    Text(
                        text = choice.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choice.options.forEachIndexed { index, option ->
                            FilterChip(
                                selected = index == choice.selected,
                                onClick = { choice.onSelect(index) },
                                label = { Text(option, maxLines = 1) },
                            )
                        }
                    }
                    if (choice.summary.isNotEmpty()) {
                        Text(
                            text = choice.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.filter_reset)) }
                Button(onClick = { open = false }) { Text(stringResource(R.string.filter_done)) }
            }
        }
    }
}
