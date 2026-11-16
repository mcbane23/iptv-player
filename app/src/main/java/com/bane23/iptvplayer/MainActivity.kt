package com.bane23.iptvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bane23.iptvplayer.data.lan.TransferTarget
import com.bane23.iptvplayer.ui.common.DeviceType
import com.bane23.iptvplayer.ui.common.UpdateDialog
import com.bane23.iptvplayer.ui.common.theme.IptvTheme
import com.bane23.iptvplayer.ui.mobile.MobileApp
import com.bane23.iptvplayer.ui.tv.TvApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Adresa TV-a iz linka `iptvplayer://send`, dok je ekran za slanje ne preuzme. */
    private var sendTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = DeviceType.isTv(this)
        if (!isTv) enableEdgeToEdge()
        // Posle obnavljanja aktivnosti link je već obrađen i ekran je na steku.
        if (savedInstanceState == null) sendTarget = sendTargetOf(intent)
        setContent {
            IptvTheme {
                if (isTv) {
                    TvApp()
                } else {
                    MobileApp(sendTarget = sendTarget, onSendTargetHandled = { sendTarget = null })
                }
                UpdateDialog()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sendTargetOf(intent)?.let { sendTarget = it }
    }

    /** Adresa iz linka sa stranice TV-a. Proverava je ekran za slanje, pre nego što išta pošalje. */
    private fun sendTargetOf(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        if (data.scheme != TransferTarget.SCHEME || data.host != TransferTarget.HOST) return null
        return data.getQueryParameter(TransferTarget.PARAM_TO)
    }
}
