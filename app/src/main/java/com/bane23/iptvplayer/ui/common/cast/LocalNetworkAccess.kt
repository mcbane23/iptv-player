package com.bane23.iptvplayer.ui.common.cast

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Od Androida 17 pristup uređajima u kućnoj mreži traži dozvolu korisnika. */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_PERMISSION_SINCE = 37

/**
 * Da li aplikacija sme da pristupa uređajima u kućnoj mreži. Na starijem Androidu uvek sme; na
 * novijem pita korisnika pri prvom prikazu.
 */
@Composable
fun rememberLocalNetworkAccess(): Boolean {
    if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SINCE) return true
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(LOCAL_NETWORK_PERMISSION)
    }
    return granted
}
