package com.bane23.iptvplayer.ui.tv

import android.app.Activity
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.player.DisplayModeInfo
import com.bane23.iptvplayer.player.DisplayModes
import com.bane23.iptvplayer.ui.common.SettingsViewModel

/**
 * Dok je plejer otvoren, prebacuje TV na osvežavanje koje odgovara broju sličica snimka (50 Hz za
 * 25 i 50 sličica, 24 Hz za filmove), ako je to uključeno u podešavanjima. Pri izlasku iz plejera
 * vraća se režim koji sistem inače koristi. Kad strim ne javlja broj sličica, ništa se ne menja.
 */
@Composable
fun MatchDisplayFrameRate(frameRate: Float, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val activity = LocalActivity.current
    val enabled by settingsViewModel.matchFrameRate.collectAsStateWithLifecycle()
    LaunchedEffect(activity, enabled, frameRate) {
        if (activity == null) return@LaunchedEffect
        if (!enabled) {
            setPreferredMode(activity, 0)
            return@LaunchedEffect
        }
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        } ?: return@LaunchedEffect
        val modes = display.supportedModes.map { DisplayModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        DisplayModes.pick(modes, display.mode.modeId, frameRate)?.let { setPreferredMode(activity, it) }
    }
    DisposableEffect(activity) {
        onDispose { if (activity != null) setPreferredMode(activity, 0) }
    }
}

private fun setPreferredMode(activity: Activity, modeId: Int) {
    val window = activity.window ?: return
    val attributes = window.attributes
    if (attributes.preferredDisplayModeId == modeId) return
    attributes.preferredDisplayModeId = modeId
    window.attributes = attributes
}
