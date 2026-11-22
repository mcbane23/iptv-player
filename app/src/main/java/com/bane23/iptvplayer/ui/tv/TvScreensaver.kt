package com.bane23.iptvplayer.ui.tv

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.UserActivity
import com.bane23.iptvplayer.ui.common.rememberClockText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Čuvar ekrana na TV-u: kad se izabrano vreme ne pritisne nijedan taster, preko svega se prikazuje
 * crn ekran sa satom i datumom koji se polako sele. Bilo koji taster ga gasi, a fokus ostaje gde je bio,
 * jer je čuvar u svom prozoru.
 *
 * [blocked] je tačno dok je otvoren plejer ili ekran na kome se čeka (npr. uparivanje sa telefonom).
 */
@Composable
fun TvScreensaver(blocked: Boolean, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val minutes by settingsViewModel.screensaverMinutes.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(minutes, blocked, lifecycleOwner) {
        visible = false
        if (minutes <= 0 || blocked) return@LaunchedEffect
        // Vreme teče samo dok je aplikacija u prvom planu; po povratku u nju kreće iz početka.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            UserActivity.ticks.collectLatest {
                visible = false
                delay(minutes * MINUTE_MS)
                visible = true
            }
        }
    }
    if (visible) ScreensaverWindow(onDismiss = UserActivity::touch)
}

@Composable
private fun ScreensaverWindow(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.tryRequestFocus() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Taster samo gasi čuvar i ne stiže do ekrana ispod. Gasi se na otpuštanje, da
                // otpuštanje istog tastera ne stigne do ekrana ispod.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .focusRequester(focus)
                .focusable()
                // Margine zbog overscan-a na televizorima.
                .padding(horizontal = 48.dp, vertical = 27.dp),
        ) {
            DriftingClock()
        }
    }
}

/** Sat i datum koji polako klize od jednog do drugog nasumičnog mesta, da ništa ne stoji na istom mestu. */
@Composable
private fun DriftingClock() {
    val clock by rememberClockText()
    val date = remember(clock) { dateText() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var content by remember { mutableStateOf(IntSize.Zero) }
    val x = remember { Animatable(Random.nextFloat()) }
    val y = remember { Animatable(Random.nextFloat()) }
    LaunchedEffect(Unit) {
        while (true) {
            // Zastoj između dva pomeranja; ujedno čuva petlju ako su animacije na uređaju isključene.
            delay(DRIFT_REST_MS)
            val nextX = Random.nextFloat()
            val nextY = Random.nextFloat()
            coroutineScope {
                launch { x.animateTo(nextX, tween(DRIFT_MOVE_MS, easing = LinearEasing)) }
                launch { y.animateTo(nextY, tween(DRIFT_MOVE_MS, easing = LinearEasing)) }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { area = it }) {
        Column(
            modifier = Modifier
                .onSizeChanged { content = it }
                // Pomeranje samo u fazi crtanja, bez ponovnog slaganja ekrana.
                .graphicsLayer {
                    translationX = (area.width - content.width).coerceAtLeast(0) * x.value
                    translationY = (area.height - content.height).coerceAtLeast(0) * y.value
                },
        ) {
            BasicText(
                text = clock,
                maxLines = 1,
                style = TextStyle(color = ClockColor, fontSize = 112.sp, fontWeight = FontWeight.Light),
            )
            BasicText(
                text = date,
                maxLines = 1,
                style = TextStyle(color = DateColor, fontSize = 22.sp),
            )
        }
    }
}

/** Dan u nedelji i datum na jeziku aplikacije, npr. „nedelja, 22. novembar”. */
private fun dateText(): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    return SimpleDateFormat(pattern, locale).format(Date())
}

private val ClockColor = Color(0xFFE6E9ED)
private val DateColor = Color(0xFFB4BCC6)
private const val MINUTE_MS = 60_000L
private const val DRIFT_REST_MS = 15_000L
private const val DRIFT_MOVE_MS = 30_000
