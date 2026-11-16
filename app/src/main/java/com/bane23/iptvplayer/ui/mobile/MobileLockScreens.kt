package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Pin
import com.bane23.iptvplayer.ui.common.lock.GateStatus
import com.bane23.iptvplayer.ui.common.lock.LockItem
import com.bane23.iptvplayer.ui.common.lock.LockTarget
import com.bane23.iptvplayer.ui.common.lock.ParentalError
import com.bane23.iptvplayer.ui.common.lock.ParentalStep
import com.bane23.iptvplayer.ui.common.lock.ParentalViewModel
import com.bane23.iptvplayer.ui.common.lock.PinGateViewModel

/** Unos PIN-a od četiri cifre; četvrta cifra ga odmah šalje. */
@Composable
fun MobilePinEntry(
    title: String,
    error: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                val digits = value.filter { it.isDigit() }.take(Pin.LENGTH)
                if (digits.length == Pin.LENGTH) {
                    text = ""
                    onSubmit(digits)
                } else {
                    text = digits
                }
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .padding(top = 16.dp)
                .width(200.dp)
                .focusRequester(focus),
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Pokazuje [content] tek kada sadržaj nije zaključan ili je unet tačan PIN. */
@Composable
fun MobilePinGate(
    target: LockTarget,
    onCancel: () -> Unit,
    viewModel: PinGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    LaunchedEffect(target) { viewModel.check(target) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.status) {
        GateStatus.OPEN -> content()

        GateStatus.CHECKING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        GateStatus.LOCKED -> Scaffold { padding ->
            Column(modifier = Modifier.padding(padding)) {
                SectionTopBar(title = stringResource(R.string.pin_locked_title), onBack = onCancel)
                MobilePinEntry(
                    title = stringResource(R.string.pin_enter),
                    error = if (state.wrongPin) stringResource(R.string.pin_wrong) else null,
                    onSubmit = viewModel::submit,
                )
            }
        }
    }
}

/** Roditeljska zaštita na telefonu: PIN i izbor zaključanih kategorija. */
@Composable
fun MobileParentalScreen(onBack: () -> Unit, viewModel: ParentalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.parental_title), onBack = onBack)
            val error = when (state.error) {
                ParentalError.WRONG_PIN -> stringResource(R.string.pin_wrong)
                ParentalError.MISMATCH -> stringResource(R.string.pin_mismatch)
                null -> null
            }
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))

                state.step == ParentalStep.MANAGE -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.parental_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item(key = "actions") {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = viewModel::lockAgain, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.parental_lock_again))
                            }
                            OutlinedButton(onClick = viewModel::changePin, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.parental_change_pin))
                            }
                            OutlinedButton(onClick = viewModel::removePin, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.parental_remove_pin))
                            }
                        }
                    }
                    section("channels", R.string.player_channels, state.channels, viewModel::toggle)
                    section("movies", R.string.vod_movies, state.movies, viewModel::toggle)
                    section("series", R.string.vod_series, state.series, viewModel::toggle)
                }

                else -> key(state.step) {
                    Column {
                        if (state.step == ParentalStep.NEW_PIN) {
                            Text(
                                text = stringResource(R.string.parental_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        MobilePinEntry(
                            title = stringResource(
                                when (state.step) {
                                    ParentalStep.NEW_PIN -> R.string.pin_new
                                    ParentalStep.REPEAT_PIN -> R.string.pin_repeat
                                    else -> R.string.pin_enter
                                },
                            ),
                            error = error,
                            onSubmit = viewModel::submit,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    key: String,
    title: Int,
    items: List<LockItem>,
    onToggle: (LockItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "title-$key") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.key }) { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(item) }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = item.locked, onCheckedChange = { onToggle(item) })
        }
    }
}
