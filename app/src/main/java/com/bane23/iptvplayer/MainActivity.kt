package com.bane23.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bane23.iptvplayer.ui.common.DeviceType
import com.bane23.iptvplayer.ui.common.theme.IptvTheme
import com.bane23.iptvplayer.ui.mobile.MobileApp
import com.bane23.iptvplayer.ui.tv.TvApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = DeviceType.isTv(this)
        if (!isTv) enableEdgeToEdge()
        setContent {
            IptvTheme {
                if (isTv) TvApp() else MobileApp()
            }
        }
    }
}
