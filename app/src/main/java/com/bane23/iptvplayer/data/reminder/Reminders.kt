package com.bane23.iptvplayer.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bane23.iptvplayer.MainActivity
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Podsetnik za emisiju iz vodiča: javlja se kad emisija počne. */
data class Reminder(
    val playlistId: Long,
    val channelKey: String,
    val startMs: Long,
    val channelName: String,
    val title: String,
) {
    /** Isti oblik kao ključ emisije u vodiču. */
    val key: String get() = key(playlistId, channelKey, startMs)

    /** Vreme je da se javi: emisija je počela, a nije prošlo toliko da podsetnik više nema smisla. */
    fun isDue(now: Long): Boolean = now >= startMs && now < startMs + KEEP_AFTER_START_MS

    fun isExpired(now: Long): Boolean = now >= startMs + KEEP_AFTER_START_MS

    fun encode(): String = listOf(playlistId, channelKey, startMs, channelName, title).joinToString(SEPARATOR)

    companion object {
        const val KEEP_AFTER_START_MS = 15 * 60_000L

        /** Znak koji se ne pojavljuje u nazivima kanala i emisija. */
        private const val SEPARATOR = "\u001F"

        fun key(playlistId: Long, channelKey: String, startMs: Long): String = "$playlistId|$channelKey|$startMs"

        fun decode(text: String): Reminder? {
            val parts = text.split(SEPARATOR)
            if (parts.size != 5) return null
            return Reminder(
                playlistId = parts[0].toLongOrNull() ?: return null,
                channelKey = parts[1],
                startMs = parts[2].toLongOrNull() ?: return null,
                channelName = parts[3],
                title = parts[4],
            )
        }
    }
}

/** Podsetnici se čuvaju u podešavanjima (malo ih je), a u zakazano vreme ih budi budilnik. */
@Singleton
class ReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val channels: ChannelRepository,
) {
    val reminders: Flow<List<Reminder>> = settings.reminders
        .map { set -> set.mapNotNull(Reminder::decode).sortedBy { it.startMs } }
        .distinctUntilChanged()

    /** Budilnik je upravo stigao; ekran odmah proverava ima li podsetnika za prikaz. */
    private val alarmAt = MutableStateFlow(0L)

    /** Podsetnik koji sada treba prikazati, ili null. Proverava se i povremeno, ako budilnik zakasni. */
    val due: Flow<Reminder?> = combine(reminders, alarmAt, ticker()) { list, _, _ ->
        val now = System.currentTimeMillis()
        list.firstOrNull { it.isDue(now) }
    }.distinctUntilChanged()

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(CHECK_EVERY_MS)
        }
    }

    suspend fun add(reminder: Reminder) {
        settings.editReminders { it + reminder.encode() }
        ReminderAlarms.set(context, reminder)
    }

    suspend fun remove(reminder: Reminder) {
        settings.editReminders { set -> set.filterNot { Reminder.decode(it)?.key == reminder.key }.toSet() }
        ReminderAlarms.cancel(context, reminder)
        NotificationManagerCompat.from(context).cancel(reminder.key.hashCode())
    }

    /** ID kanala na koji podsetnik vodi, ako kanal još postoji na listi. */
    suspend fun channelId(reminder: Reminder): Long? = channels.channelIdByKey(reminder.playlistId, reminder.channelKey)

    /** Posle paljenja uređaja budilnici ne postoje; vraća ih i briše podsetnike kojima je vreme prošlo. */
    suspend fun restoreAlarms() {
        val now = System.currentTimeMillis()
        settings.editReminders { set -> set.filter { Reminder.decode(it)?.isExpired(now) == false }.toSet() }
        for (reminder in reminders.first()) {
            if (reminder.startMs > now) ReminderAlarms.set(context, reminder)
        }
    }

    /** Budilnik je stigao: javlja ekranu i, kad su obaveštenja dozvoljena, prikazuje obaveštenje. */
    suspend fun onAlarm(key: String) {
        val reminder = reminders.first().firstOrNull { it.key == key } ?: return
        alarmAt.value = System.currentTimeMillis()
        notify(reminder)
    }

    private fun notify(reminder: Reminder) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(context.getString(R.string.reminder_starts, reminder.title))
            .setContentText(reminder.channelName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(Reminder.KEEP_AFTER_START_MS)
            .build()
        try {
            manager.notify(reminder.key.hashCode(), notification)
        } catch (_: SecurityException) {
            // Dozvola je povučena u međuvremenu; podsetnik se vidi kad se aplikacija otvori.
        }
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val CHECK_EVERY_MS = 20_000L
    }
}

/** Budilnici podsetnika; rade i kad aplikacija nije otvorena. */
object ReminderAlarms {
    private const val EXTRA_KEY = "reminder_key"

    fun set(context: Context, reminder: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context, reminder.key)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startMs, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startMs, intent)
            }
        } catch (_: SecurityException) {
            alarms.set(AlarmManager.RTC_WAKEUP, reminder.startMs, intent)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, reminder.key))
    }

    fun key(intent: Intent): String? = intent.getStringExtra(EXTRA_KEY)

    private fun pending(context: Context, key: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            Intent(context, ReminderAlarmReceiver::class.java).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Prima budilnik podsetnika. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val key = ReminderAlarms.key(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reminders.onAlarm(key)
            } finally {
                pending.finish()
            }
        }
    }
}
