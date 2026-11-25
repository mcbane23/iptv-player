package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.domain.Pin
import androidx.compose.ui.platform.LocalContext
import com.bane23.iptvplayer.ui.common.AppMessages
import com.bane23.iptvplayer.ui.common.lock.GateStatus
import com.bane23.iptvplayer.ui.common.lock.LockItem
import com.bane23.iptvplayer.ui.common.lock.LockTarget
import com.bane23.iptvplayer.ui.common.lock.ParentalError
import com.bane23.iptvplayer.ui.common.lock.ParentalStep
import com.bane23.iptvplayer.ui.common.lock.ParentalViewModel
import com.bane23.iptvplayer.ui.common.lock.PinGateViewModel
import com.bane23.iptvplayer.ui.common.vod.VodSearch

private const val DELETE_KEY = "⌫"

/**
 * Unos PIN-a na TV-u: cifre se kucaju tasterima na daljinskom ili biraju na tastaturi na ekranu.
 * Četvrta cifra odmah šalje PIN.
 */
@Composable
fun TvPinEntry(
    title: String,
    error: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var digits by remember { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }

    fun typeDigit(digit: Char) {
        val next = digits + digit
        if (next.length >= Pin.LENGTH) {
            digits = ""
            onSubmit(next.take(Pin.LENGTH))
        } else {
            digits = next
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                val digit = remoteDigit(event.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) typeDigit(digit)
                true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = (0 until Pin.LENGTH).joinToString("  ") { if (it < digits.length) "●" else "○" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Text(
            text = error ?: stringResource(R.string.pin_hint_tv),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", DELETE_KEY))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { label ->
                        if (label.isEmpty()) {
                            Box(modifier = Modifier.width(72.dp))
                        } else {
                            TvButton(
                                onClick = {
                                    if (label == DELETE_KEY) digits = digits.dropLast(1) else typeDigit(label[0])
                                },
                                modifier = Modifier
                                    .width(72.dp)
                                    .then(if (label == "1") Modifier.focusRequester(firstKey) else Modifier),
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { firstKey.tryRequestFocus() }
}

/** Cifra sa daljinskog upravljača (gornji red ili numerički deo), ili null za ostale tastere. */
private fun remoteDigit(keyCode: Int): Char? = when (keyCode) {
    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> '0' + (keyCode - AndroidKeyEvent.KEYCODE_0)
    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> '0' + (keyCode - AndroidKeyEvent.KEYCODE_NUMPAD_0)
    else -> null
}

/** Pokazuje [content] tek kada sadržaj nije zaključan ili je unet tačan PIN. Nazad odustaje. */
@Composable
fun TvPinGate(
    target: LockTarget,
    viewModel: PinGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    LaunchedEffect(target) { viewModel.check(target) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.status) {
        GateStatus.OPEN -> content()

        GateStatus.CHECKING -> Box(
            modifier = Modifier
                .fillMaxSize()
                .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background),
        )

        GateStatus.LOCKED -> Column(
            modifier = Modifier
                .fillMaxSize()
                .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
                .padding(horizontal = 48.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.pin_locked_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            TvPinEntry(
                title = stringResource(R.string.pin_enter),
                error = if (state.wrongPin) stringResource(R.string.pin_wrong) else null,
                onSubmit = viewModel::submit,
            )
        }
    }
}

/** Roditeljska zaštita na TV-u: PIN i izbor zaključanih kategorija. */
@Composable
fun TvParentalScreen(onBack: () -> Unit, viewModel: ParentalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Posle zaključavanja ekran se zatvara: PIN se traži tek pri sledećem ulasku. Dok se zatvara, ostaje
    // spisak, da unos PIN-a ne sevne na trenutak.
    var closing by remember { mutableStateOf(false) }
    val lockAgain = {
        closing = true
        viewModel.lockAgain()
        AppMessages.show(context, R.string.parental_locked)
        onBack()
    }
    val firstRow = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.parental_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        val error = when (state.error) {
            ParentalError.WRONG_PIN -> stringResource(R.string.pin_wrong)
            ParentalError.MISMATCH -> stringResource(R.string.pin_mismatch)
            null -> null
        }
        when {
            state.loading -> Unit

            state.step == ParentalStep.MANAGE || closing -> {
                val locked = stringResource(R.string.parental_locked)
                val open = stringResource(R.string.parental_unlocked)
                var filter by rememberSaveable { mutableStateOf("") }
                val needle = remember(filter) { VodSearch.normalize(filter) }
                fun found(items: List<LockItem>) =
                    if (needle.isEmpty()) items else items.filter { VodSearch.normalize(it.name).contains(needle) }
                val channels = remember(state.channels, needle) { found(state.channels) }
                val movies = remember(state.movies, needle) { found(state.movies) }
                val series = remember(state.series, needle) { found(state.series) }
                val single = remember(state.single, needle) { found(state.single) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(0.75f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.parental_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                    item(key = "lock-again") {
                        TvTextRow(
                            text = stringResource(R.string.parental_lock_again),
                            onClick = lockAgain,
                            modifier = Modifier.focusRequester(firstRow),
                        )
                    }
                    item(key = "change-pin") {
                        TvTextRow(text = stringResource(R.string.parental_change_pin), onClick = viewModel::changePin)
                    }
                    item(key = "remove-pin") {
                        TvTextRow(text = stringResource(R.string.parental_remove_pin), onClick = viewModel::removePin)
                    }
                    item(key = "search") {
                        TvFilterField(
                            value = filter,
                            onChange = { filter = it },
                            placeholder = stringResource(R.string.search_categories),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    if (single.isEmpty() && channels.isEmpty() && movies.isEmpty() && series.isEmpty() && needle.isNotEmpty()) {
                        item(key = "nothing-found") {
                            Text(
                                text = stringResource(R.string.search_empty_categories),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                            )
                        }
                    }
                    section("single", R.string.parental_single, single, locked, open, viewModel::toggle)
                    section("channels", R.string.player_channels, channels, locked, open, viewModel::toggle)
                    section("movies", R.string.vod_movies, movies, locked, open, viewModel::toggle)
                    section("series", R.string.vod_series, series, locked, open, viewModel::toggle)
                }
                LaunchedEffect(Unit) { firstRow.tryRequestFocus() }
            }

            else -> key(state.step) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.step == ParentalStep.NEW_PIN) {
                        Text(
                            text = stringResource(R.string.parental_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                    TvPinEntry(
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

private fun LazyListScope.section(
    key: String,
    title: Int,
    items: List<LockItem>,
    lockedText: String,
    openText: String,
    onToggle: (LockItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "title-$key") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.key }) { item ->
        TvTextRow(
            text = item.name,
            trailing = if (item.locked) lockedText else openText,
            checked = item.locked,
            onClick = { onToggle(item) },
        )
    }
}
