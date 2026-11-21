package com.bane23.iptvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bane23.iptvplayer.data.lan.TransferTarget
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.ui.common.DeviceType
import com.bane23.iptvplayer.ui.common.AppMessageHost
import com.bane23.iptvplayer.ui.common.CrashReportPrompt
import com.bane23.iptvplayer.ui.common.UpdateDialog
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.watchnext.WatchNextLink
import com.bane23.iptvplayer.ui.common.theme.AccentColor
import com.bane23.iptvplayer.ui.common.theme.AppTheme
import com.bane23.iptvplayer.ui.common.theme.IptvTheme
import com.bane23.iptvplayer.ui.common.theme.ScaledUi
import com.bane23.iptvplayer.ui.common.theme.UiScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.ui.mobile.AppFullScreen
import com.bane23.iptvplayer.ui.mobile.MobileApp
import com.bane23.iptvplayer.ui.tv.TvApp
import com.bane23.iptvplayer.ui.tv.TvEdgeScroll
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Adresa TV-a iz linka `iptvplayer://send`, dok je ekran za slanje ne preuzme. */
    private var sendTarget by mutableStateOf<String?>(null)

    /** Film ili serija izabrana u redu „Nastavite sa gledanjem” na početnom ekranu TV-a. */
    private var watchLink by mutableStateOf<WatchNextLink?>(null)

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var trakt: TraktRepository

    /** Jezik izabran u podešavanjima važi za sve tekstove aktivnosti. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    /** Pri svakom povratku u aplikaciju preuzima se ono što je u međuvremenu gledano na drugom uređaju. */
    override fun onStart() {
        super.onStart()
        trakt.refreshIfStale()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = DeviceType.isTv(this)
        if (!isTv) enableEdgeToEdge()
        // Posle obnavljanja aktivnosti link je već obrađen i ekran je na steku.
        if (savedInstanceState == null) {
            sendTarget = sendTargetOf(intent)
            watchLink = WatchNextLink.of(intent)
        }
        // Boja se čita pre prvog crtanja, da se pri pokretanju ne vidi podrazumevana pa tek onda izabrana.
        val savedAccent = runBlocking { settingsStore.accentColor.first() }
        val savedBlack = runBlocking { settingsStore.blackTheme.first() }
        val savedTheme = runBlocking { settingsStore.appTheme.first() }
        // Isto važi za veličinu prikaza, da se ekran pri pokretanju ne iscrta pa tek onda smanji.
        val savedScale = runBlocking { settingsStore.uiScale.first() }
        // I ceo ekran na telefonu, da se trake pri pokretanju ne pojave pa tek onda sakriju.
        val savedFullScreen = !isTv && runBlocking { settingsStore.appFullScreen.first() }
        setContent {
            val accent by settingsStore.accentColor.collectAsStateWithLifecycle(initialValue = savedAccent)
            val scale by settingsStore.uiScale.collectAsStateWithLifecycle(initialValue = savedScale)
            val black by settingsStore.blackTheme.collectAsStateWithLifecycle(initialValue = savedBlack)
            val theme by settingsStore.appTheme.collectAsStateWithLifecycle(initialValue = savedTheme)
            IptvTheme(accent = AccentColor.fromName(accent), theme = AppTheme.fromName(theme), black = black) {
                ScaledUi(UiScale.fromName(scale, isTv)) {
                    if (isTv) {
                        // Na celom TV interfejsu se pomera fokus, a spisak tek kad fokus priđe ivici.
                        TvEdgeScroll { TvApp(watchLink = watchLink, onWatchLinkHandled = { watchLink = null }) }
                    } else {
                        val fullScreen by settingsStore.appFullScreen.collectAsStateWithLifecycle(initialValue = savedFullScreen)
                        AppFullScreen(enabled = fullScreen) {
                            MobileApp(sendTarget = sendTarget, onSendTargetHandled = { sendTarget = null })
                        }
                    }
                    UpdateDialog()
                    CrashReportPrompt()
                    AppMessageHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sendTargetOf(intent)?.let { sendTarget = it }
        WatchNextLink.of(intent)?.let { watchLink = it }
    }

    /** Adresa iz linka sa stranice TV-a. Proverava je ekran za slanje, pre nego što išta pošalje. */
    private fun sendTargetOf(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        if (data.scheme != TransferTarget.SCHEME || data.host != TransferTarget.HOST) return null
        return data.getQueryParameter(TransferTarget.PARAM_TO)
    }
}
