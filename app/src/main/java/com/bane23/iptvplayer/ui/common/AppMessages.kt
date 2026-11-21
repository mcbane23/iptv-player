package com.bane23.iptvplayer.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kratke poruke aplikacije („Kanal je sakriven”, „Pritisnite ponovo za izlaz”…). Umesto sistemske
 * poruke, koja na svakom uređaju izgleda drugačije, crta ih [AppMessageHost] kao tamnu pločicu pri
 * dnu ekrana, isto na telefonu i na TV-u. Nova poruka zamenjuje prethodnu.
 */
object AppMessages {
    /** Poruka na ekranu. [id] razlikuje dve iste poruke zaredom, da se vreme prikaza računa iznova. */
    data class Message(val id: Long, val text: String, val durationMs: Long)

    const val SHORT_MS = 3_000L
    const val LONG_MS = 5_000L

    private val _current = MutableStateFlow<Message?>(null)
    val current: StateFlow<Message?> = _current.asStateFlow()

    private var nextId = 0L

    /** Prikazuje poruku. [long] je za duži tekst koji treba pročitati, ostaje malo duže. */
    fun show(text: CharSequence, long: Boolean = false) {
        val id = synchronized(this) { ++nextId }
        _current.value = Message(id, text.toString(), if (long) LONG_MS else SHORT_MS)
    }

    fun show(context: Context, @StringRes text: Int, long: Boolean = false) = show(context.getString(text), long)

    /** Sklanja poruku kad joj istekne vreme, ako je u međuvremenu nije zamenila novija. */
    fun dismiss(message: Message) {
        _current.compareAndSet(message, null)
    }
}

private val MessageShape = RoundedCornerShape(12.dp)
private val MessageBackground = Color(0xF21C1C20)
private val MessageBorder = Color.White.copy(alpha = 0.16f)
private val MessageText = TextStyle(
    color = Color.White,
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
)

/** Crta poruke iz [AppMessages] preko celog sadržaja aplikacije. Stavlja se jednom, u korenu. */
@Composable
fun AppMessageHost() {
    val current by AppMessages.current.collectAsStateWithLifecycle()
    // Tekst ostaje zapamćen i dok pločica nestaje, da se ne isprazni pre kraja animacije.
    var shown by remember { mutableStateOf<AppMessages.Message?>(null) }
    current?.let { shown = it }
    LaunchedEffect(current) {
        val message = current ?: return@LaunchedEffect
        delay(message.durationMs)
        AppMessages.dismiss(message)
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
        AnimatedVisibility(
            visible = current != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            BasicText(
                text = shown?.text.orEmpty(),
                style = MessageText,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .background(MessageBackground, MessageShape)
                    .border(1.dp, MessageBorder, MessageShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
