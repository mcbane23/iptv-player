package com.bane23.iptvplayer.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bane23.iptvplayer.MainActivity
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.AppLocale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Drži aplikaciju živom dok traje preuzimanje (obaveštenje sa napretkom i dugmetom „Pauziraj”).
 * Samo preuzimanje vodi [DownloadRepository]; servis se gasi kad se više ništa ne preuzima.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloads: DownloadRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Servis sme da se ugasi tek pošto je pokazao obaveštenje (sistem to traži od svakog pokretanja). */
    private var foreground = false

    /** Drži procesor budnim dok se preuzima, i kad je ekran ugašen. */
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** Obaveštenje je na jeziku izabranom u podešavanjima. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        scope.launch {
            downloads.active.collectLatest { active ->
                if (!foreground) return@collectLatest
                if (active == null) {
                    // Između dva preuzimanja iz reda prođe trenutak; servis se zbog toga ne gasi.
                    delay(IDLE_STOP_MS)
                    finish()
                } else {
                    getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(active))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Servis mora odmah da pokaže obaveštenje, čak i ako je preuzimanje u međuvremenu stalo.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(downloads.active.value), type)
        foreground = true
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iptvplayer:download")
                ?.also { it.acquire(MAX_WAKE_MS) }
        }
        if (intent?.action == ACTION_PAUSE) scope.launch { downloads.pauseActive() }
        if (downloads.active.value == null) {
            // Pokrenut je, a ništa se ne preuzima (npr. upravo pauzirano): gasi se ako tako i ostane.
            scope.launch {
                delay(IDLE_STOP_MS)
                if (downloads.active.value == null) finish()
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(active: ActiveDownload?): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), immutable)
        val pause = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE),
            immutable,
        )
        val percent = active?.let { DownloadRules.percent(it.bytes, it.totalBytes) }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(listOfNotNull(active?.title, percent?.let { "$it%" }).joinToString(" · "))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (active != null) {
            builder.setProgress(100, percent ?: 0, percent == null)
            builder.addAction(0, getString(R.string.download_pause), pause)
        }
        return builder.build()
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 43
        const val IDLE_STOP_MS = 5_000L

        /** Najduže držanje procesora budnim; duže preuzimanje jednog reda se ne očekuje. */
        const val MAX_WAKE_MS = 12 * 60 * 60 * 1000L
        const val ACTION_PAUSE = "com.bane23.iptvplayer.download.PAUSE"
    }
}
