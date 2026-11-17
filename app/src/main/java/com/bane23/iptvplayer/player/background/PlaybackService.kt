package com.bane23.iptvplayer.player.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import com.bane23.iptvplayer.MainActivity
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Drži aplikaciju živom dok plejer na telefonu pušta sa ugašenim ekranom ili iz pozadine.
 * Obaveštenje i zaključan ekran nude pauzu i zaustavljanje, a rade i dugmad na slušalicama.
 * Sam plejer ostaje kod ekrana plejera, vidi [BackgroundPlayback].
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null

    /** Servis sme da se ugasi tek pošto je pokazao obaveštenje (sistem to traži od svakog pokretanja). */
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    /** Obaveštenje je na jeziku izabranom u podešavanjima. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_play_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        session = MediaSession(this, "playback").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = setPlaying(true)

                    override fun onPause() = setPlaying(false)

                    override fun onStop() = stopPlayback()

                    override fun onCustomAction(action: String, extras: Bundle?) {
                        if (action == CUSTOM_STOP) stopPlayback()
                    }
                },
            )
        }
        scope.launch {
            BackgroundPlayback.target.collectLatest { target ->
                if (target == null) {
                    if (foreground) shutdown()
                    return@collectLatest
                }
                // Zaboravljena pauza ne drži servis i vezu sa serverom: posle nekog vremena se sve zaustavlja.
                var pauseJob: Job? = null
                fun watchPause() {
                    pauseJob?.cancel()
                    pauseJob = if (target.player.playWhenReady) {
                        null
                    } else {
                        scope.launch {
                            delay(PAUSE_STOP_MS)
                            stopPlayback()
                        }
                    }
                }
                val listener = object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        publish(target)
                        watchPause()
                    }
                }
                target.player.addListener(listener)
                try {
                    publish(target)
                    watchPause()
                    awaitCancellation()
                } finally {
                    pauseJob?.cancel()
                    target.player.removeListener(listener)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        try {
            // Servis mora odmah da pokaže obaveštenje, čak i ako je plejer u međuvremenu zatvoren.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(BackgroundPlayback.target.value), type)
            foreground = true
        } catch (_: IllegalStateException) {
            // Sistem ne dozvoljava servis iz pozadine: puštanje se zaustavlja kao da opcije nema.
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TOGGLE -> BackgroundPlayback.target.value?.toggle?.invoke()
            ACTION_STOP -> stopPlayback()
        }
        if (!BackgroundPlayback.active) shutdown()
        return START_NOT_STICKY
    }

    /** Aplikacija je sklonjena iz liste nedavnih: puštanje ne ostaje da radi samo. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlayback()
        if (!foreground) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun setPlaying(playing: Boolean) {
        val target = BackgroundPlayback.target.value ?: return
        if (target.player.playWhenReady != playing) target.toggle()
    }

    private fun stopPlayback() {
        val target = BackgroundPlayback.target.value ?: return
        target.stop()
        BackgroundPlayback.stop()
    }

    private fun shutdown() {
        session?.isActive = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    /** Stanje plejera se prenosi u obaveštenje, na zaključan ekran i dugmad na slušalicama. */
    private fun publish(target: BackgroundPlayback.Target) {
        val playing = target.player.playWhenReady
        session?.apply {
            setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, target.title).build())
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP,
                    )
                    .setState(
                        if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        if (playing) 1f else 0f,
                    )
                    .addCustomAction(
                        PlaybackState.CustomAction.Builder(
                            CUSTOM_STOP,
                            getString(R.string.background_play_stop),
                            R.drawable.ic_notif_stop,
                        ).build(),
                    )
                    .build(),
            )
            isActive = true
        }
        if (foreground) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(target))
        }
    }

    private fun notification(target: BackgroundPlayback.Target?): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        // Isti poziv kao sa početnog ekrana: vraća otvorenu aplikaciju umesto da pravi novu.
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        val open = PendingIntent.getActivity(this, 0, launch, immutable)
        val playing = target?.player?.playWhenReady == true
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.ic_notif_play)
            .setContentTitle(target?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name))
            .setContentText(getString(R.string.background_play_channel))
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                action(
                    if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                    getString(if (playing) R.string.player_pause else R.string.player_play),
                    ACTION_TOGGLE,
                    requestCode = 1,
                ),
            )
            .addAction(action(R.drawable.ic_notif_stop, getString(R.string.background_play_stop), ACTION_STOP, requestCode = 2))
        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1)
        session?.let { style.setMediaSession(it.sessionToken) }
        return builder.setStyle(style).build()
    }

    private fun action(icon: Int, label: String, action: String, requestCode: Int): Notification.Action {
        val intent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent).build()
    }

    private companion object {
        const val CHANNEL_ID = "background_play"
        const val NOTIFICATION_ID = 42
        const val ACTION_TOGGLE = "com.bane23.iptvplayer.playback.TOGGLE"
        const val ACTION_STOP = "com.bane23.iptvplayer.playback.STOP"
        const val CUSTOM_STOP = "stop"

        /** Koliko pauza u pozadini sme da traje pre nego što se puštanje samo zaustavi. */
        const val PAUSE_STOP_MS = 10 * 60 * 1000L
    }
}
