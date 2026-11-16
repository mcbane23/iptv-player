package com.bane23.iptvplayer.data.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Budilnici koji u zakazano vreme pokreću snimanje, i kad aplikacija nije otvorena. */
object RecordingAlarms {
    private const val EXTRA_ID = "recording_id"

    fun set(context: Context, recordingId: Long, atMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context, recordingId)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
            } else {
                // Bez dozvole za tačne budilnike snimanje može da krene sa malim zakašnjenjem.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
            }
        } catch (_: SecurityException) {
            alarms.set(AlarmManager.RTC_WAKEUP, atMs, intent)
        }
    }

    fun cancel(context: Context, recordingId: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, recordingId))
    }

    fun recordingId(intent: Intent): Long = intent.getLongExtra(EXTRA_ID, -1)

    private fun pending(context: Context, recordingId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            recordingId.toInt(),
            Intent(context, RecordingAlarmReceiver::class.java).putExtra(EXTRA_ID, recordingId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Prima budilnik zakazanog snimanja i pokreće ga. */
@AndroidEntryPoint
class RecordingAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var recordings: RecordingRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = RecordingAlarms.recordingId(intent)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recordings.startScheduled(id)
            } finally {
                pending.finish()
            }
        }
    }
}
