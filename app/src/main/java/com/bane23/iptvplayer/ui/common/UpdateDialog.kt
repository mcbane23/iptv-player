package com.bane23.iptvplayer.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.update.AppUpdater
import com.bane23.iptvplayer.data.update.UpdateChannel
import com.bane23.iptvplayer.data.update.UpdateError
import com.bane23.iptvplayer.data.update.UpdateManifest
import com.bane23.iptvplayer.data.update.UpdateNotes
import com.bane23.iptvplayer.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updater: AppUpdater) : ViewModel() {
    val state: StateFlow<UpdateState> = updater.state
    val installedVersion: String = updater.installedVersionName

    /** Stavke za „Šta je novo”: ono što ponuđena verzija donosi u odnosu na instaliranu. */
    fun whatsNew(manifest: UpdateManifest, language: String): List<String> =
        UpdateNotes.lines(manifest, updater.installedVersionCode, language)
    val channel: StateFlow<UpdateChannel> =
        updater.channel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateChannel.DEFAULT)

    fun setChannel(channel: UpdateChannel) = updater.setChannel(channel)

    fun checkOnStart() = updater.checkOnStart()
    fun check() = updater.check(userInitiated = true)
    fun update() = updater.downloadAndInstall()
    fun dismiss() = updater.dismiss()
    fun canInstall() = updater.canInstall()
    /** Dozvola se čita kad dijalog počne da prati stanje, dakle posle povratka iz sistemskih podešavanja. */
    val reopens: StateFlow<Boolean> =
        updater.reopensAfterUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
}

/**
 * Poruke o ažuriranju aplikacije, iznad svih ekrana. Pri startu tiho proverava novu verziju
 * i javlja se samo ako je ima. Radi na dodir i daljinskim (fokus odmah na glavnom dugmetu).
 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.checkOnStart() }

    // Povratak iz sistemskih podešavanja: ako je dozvola data, instalacija kreće sama.
    LifecycleResumeEffect(state) {
        if (state is UpdateState.NeedsPermission && viewModel.canInstall()) viewModel.update()
        onPauseOrDispose { }
    }

    // Šta biva posle instalacije: aplikacija se otvara sama ili je korisnik otvara ručno.
    val reopens by viewModel.reopens.collectAsStateWithLifecycle()
    val afterUpdate = stringResource(
        if (reopens) R.string.update_reopen_auto else R.string.update_reopen_manual,
    )

    // Jedan dijalog za sva stanja: menja se samo sadržaj, pa prozor ne nestaje i ne treperi
    // (npr. „Proveravam…” odmah prelazi u „Imate najnoviju verziju”).
    val language = LocalLocale.current.platformLocale.language
    val content: AlertContent? = when (val s = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> AlertContent(
            title = stringResource(R.string.update_title),
            text = stringResource(R.string.update_checking),
            progress = -1f,
            confirm = stringResource(R.string.update_close) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        UpdateState.UpToDate -> AlertContent(
            title = stringResource(R.string.update_title),
            text = stringResource(R.string.update_up_to_date, viewModel.installedVersion),
            confirm = stringResource(R.string.update_ok) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Available -> AlertContent(
            title = stringResource(R.string.update_available_title),
            text = stringResource(
                R.string.update_available_text,
                s.manifest.versionName,
                viewModel.installedVersion,
                afterUpdate,
            ),
            notes = remember(s.manifest, language) { viewModel.whatsNew(s.manifest, language) },
            confirm = stringResource(R.string.update_install) to viewModel::update,
            dismiss = stringResource(R.string.update_later) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Downloading -> AlertContent(
            title = stringResource(R.string.update_available_title),
            text = s.progress?.let { stringResource(R.string.update_downloading_percent, (it * 100).toInt()) }
                ?: stringResource(R.string.update_downloading),
            progress = s.progress ?: -1f,
            confirm = stringResource(R.string.update_cancel) to viewModel::dismiss,
            // Slučajan dodir pored dijaloga ne prekida preuzimanje.
            onDismiss = {},
        )
        is UpdateState.NeedsPermission -> AlertContent(
            title = stringResource(R.string.update_permission_title),
            text = stringResource(R.string.update_permission_text),
            confirm = stringResource(R.string.update_open_settings) to { openInstallPermission(context) },
            dismiss = stringResource(R.string.update_cancel) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Installing -> AlertContent(
            title = stringResource(R.string.update_available_title),
            // Bez uputstva o potvrdi: sistem je nekad traži, a nekad instalira bez pitanja.
            text = stringResource(R.string.update_installing) + "\n" + afterUpdate,
            progress = -1f,
            confirm = stringResource(R.string.update_close) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Failed -> AlertContent(
            title = stringResource(R.string.update_title),
            text = stringResource(
                when (s.reason) {
                    UpdateError.NETWORK -> R.string.update_error_network
                    UpdateError.BAD_FILE -> R.string.update_error_file
                    UpdateError.INSTALL -> R.string.update_error_install
                },
            ),
            confirm = stringResource(R.string.update_ok) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
    }
    if (content != null) UpdateAlert(content)
}

internal data class AlertContent(
    val title: String,
    val text: String,
    val confirm: Pair<String, () -> Unit>,
    val onDismiss: () -> Unit,
    val dismiss: Pair<String, () -> Unit>? = null,
    /** Napredak od 0 do 1, negativno za neodređen, null bez trake. */
    val progress: Float? = null,
    /** Šta je novo, jedna stavka po redu. Prikazuje se samo dok se čeka potvrda ažuriranja. */
    val notes: List<String> = emptyList(),
)

/**
 * Prozor je od početka preko celog ekrana, a kartica se centrira unutar njega. Sistemski prozor
 * dijaloga koji se širi po sadržaju ume da se pojavi u uglu pa da doklizi do sredine.
 */
@Composable
internal fun UpdateAlert(content: AlertContent) {
    val (title, text, confirm, onDismiss, dismiss, progress, notes) = content
    val confirmFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .userActivity()
                // Dodir pored kartice zatvara; pointerInput umesto clickable da na TV-u ne prima fokus.
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .padding(24.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 480.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    if (notes.isNotEmpty()) WhatsNew(notes, Modifier.weight(1f, fill = false))
                    if (progress != null) {
                        val modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                        if (progress < 0f) {
                            LinearProgressIndicator(modifier = modifier)
                        } else {
                            LinearProgressIndicator(progress = { progress }, modifier = modifier)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) {
                        dismiss?.let { (label, action) ->
                            TextButton(onClick = action) { Text(label) }
                        }
                        TextButton(onClick = confirm.second, modifier = Modifier.focusRequester(confirmFocus)) {
                            Text(confirm.first)
                        }
                    }
                }
            }
        }
    }
    // Na TV-u fokus mora odmah biti na dugmetu, inače daljinski nema šta da pritisne.
    LaunchedEffect(title, confirm.first) { runCatching { confirmFocus.requestFocus() } }
}

/**
 * Spisak novosti iznad dugmadi. Kad ne stane, lista se prstom, a na TV-u daljinskim: sa dugmeta
 * „gore” prelazi na spisak (okvir pokazuje fokus), pa gore i dole pomeraju tekst. Sa kraja
 * spiska „dole” vraća na dugmad.
 */
@Composable
private fun WhatsNew(notes: List<String>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 72.dp.toPx() }
    var focused by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.update_whats_new),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val delta = when (event.key) {
                    Key.DirectionDown -> if (scroll.canScrollForward) step else 0f
                    Key.DirectionUp -> if (scroll.canScrollBackward) -step else 0f
                    else -> 0f
                }
                if (delta == 0f) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(delta) }
                    true
                }
            }
            // Fokus prima samo kad ima šta da se lista; kratak spisak daljinski preskače.
            .focusable(enabled = scroll.maxValue > 0)
            .verticalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        notes.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Otvara sistemsko podešavanje „Instaliranje nepoznatih aplikacija” za ovu aplikaciju. */
private fun openInstallPermission(context: Context) {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/** Naziv kanala ažuriranja za podešavanja. */
@Composable
fun UpdateChannel.label(): String = stringResource(
    when (this) {
        UpdateChannel.RELEASE -> R.string.update_channel_release
        UpdateChannel.DEV -> R.string.update_channel_dev
    },
)
