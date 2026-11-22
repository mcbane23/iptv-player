package com.bane23.iptvplayer.ui.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.ScreenPosition
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.playerPanel

/**
 * Okretanje ekrana na telefonu. Osnovni položaj dolazi iz podešavanja („Položaj ekrana”), a plejer
 * ga dugmetom privremeno menja. Aplikacija okreće samo svoj prozor, pa ovo radi i kad je rotacija
 * na telefonu zaključana, i ne dira podešavanja telefona.
 */
@Stable
class OrientationControl internal constructor(private val activity: Activity) {
    internal var base by mutableStateOf(ScreenPosition.AUTO)

    /** Položaj koji je korisnik izabrao dugmetom u plejeru; null dok važi osnovni. */
    internal var forced by mutableStateOf<Int?>(null)
        private set

    /** Koliko je plejera trenutno na ekranu; pri prelasku sa jednog na drugi kratko su oba. */
    internal var players = 0

    internal fun apply() {
        val wanted = forced ?: base.request
        if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
    }

    /** Dugme u plejeru: iz uspravnog u položen prikaz i nazad. */
    fun rotate(landscapeNow: Boolean) {
        forced = if (landscapeNow) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        apply()
    }

    /** Vraća osnovni položaj. */
    fun release() {
        forced = null
        apply()
    }

    /** Da li telefon sam okreće ekran (rotacija nije zaključana). */
    fun systemAutoRotate(): Boolean =
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
}

/** Okretanje ekrana; null van telefona (pregledi, testovi), i tada se dugme ne prikazuje. */
internal val LocalOrientationControl = staticCompositionLocalOf<OrientationControl?> { null }

/** Drži aplikaciju u položaju izabranom u podešavanjima i daje plejeru dugme za okretanje. */
@Composable
fun AppOrientation(position: ScreenPosition, content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val control = remember(activity) { activity?.let { OrientationControl(it) } }
    SideEffect {
        if (control != null && control.base != position) {
            control.base = position
            control.apply()
        }
    }
    CompositionLocalProvider(LocalOrientationControl provides control, content = content)
}

@Composable
internal fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Zove ga svaki plejer na telefonu. Kad se plejer zatvori, ekran se vraća u osnovni položaj. Ako
 * korisnik posle dugmeta i sam okrene telefon u isti položaj, a rotacija nije zaključana, izbor
 * dugmeta se otpušta, pa okretanje telefona dalje radi kao i inače.
 */
@Composable
internal fun PlayerOrientation() {
    val control = LocalOrientationControl.current ?: return
    val activity = LocalActivity.current ?: return
    DisposableEffect(control) {
        control.players++
        onDispose {
            control.players--
            if (control.players <= 0) control.release()
        }
    }
    val forced = control.forced
    DisposableEffect(control, forced, control.base) {
        if (forced == null || control.base != ScreenPosition.AUTO || !control.systemAutoRotate()) {
            return@DisposableEffect onDispose { }
        }
        val wantLandscape = forced != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val landscape = degrees in 60..120 || degrees in 240..300
                val portrait = degrees <= 30 || degrees >= 330
                if ((wantLandscape && landscape) || (!wantLandscape && portrait)) control.release()
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
}

/** Dugme za okretanje u uglu slike dok je telefon uspravno: u boji aplikacije, da se odmah vidi. */
@Composable
internal fun RotateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.ScreenRotation,
            contentDescription = stringResource(R.string.player_rotate),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Poruka koja se pojavi samo prvi put kad se nešto pusti uspravno: kaže da postoji položen prikaz i
 * nudi da okrene ekran. Posle bilo kog dugmeta, ili kad se telefon okrene, više se ne prikazuje.
 */
@Composable
internal fun RotateHint(modifier: Modifier = Modifier, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val control = LocalOrientationControl.current ?: return
    val shown by settingsViewModel.rotateHintShown.collectAsStateWithLifecycle()
    if (shown != false) return
    val landscape = isLandscape()
    // Ko je već našao položen prikaz, poruka mu ne treba.
    LaunchedEffect(landscape) { if (landscape) settingsViewModel.setRotateHintShown() }
    if (landscape) return
    val locked = remember(control) { !control.systemAutoRotate() }
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .playerPanel(RoundedCornerShape(14.dp))
            // Dodir na poruku ne sme da sakrije kontrole plejera.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppIcons.ScreenRotation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.rotate_hint_title),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(if (locked) R.string.rotate_hint_locked else R.string.rotate_hint_text),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rotate_hint_dismiss),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { settingsViewModel.setRotateHintShown() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.rotate_hint_action),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        settingsViewModel.setRotateHintShown()
                        control.rotate(landscapeNow = false)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
