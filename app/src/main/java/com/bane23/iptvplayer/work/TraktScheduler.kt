package com.bane23.iptvplayer.work

import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Zakazuje sinhronizaciju sa Trakt-om u pozadini (WorkManager). */
@Singleton
class TraktScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /** Kada je sinhronizacija poslednji put tražena (vreme od paljenja uređaja); 0: još nije. */
    @Volatile
    private var lastRequestedMs = 0L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sinhronizacija što pre: posle promene oznake, posle prijave i pri pokretanju aplikacije. */
    fun syncSoon() {
        lastRequestedMs = SystemClock.elapsedRealtime()
        val request = OneTimeWorkRequestBuilder<TraktSyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // Promena tokom slanja čeka da se ono završi, pa ide sledeća sinhronizacija.
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Sinhronizacija samo ako poslednja nije tražena u poslednjih nekoliko minuta. */
    fun syncIfStale() {
        val last = lastRequestedMs
        if (last == 0L || SystemClock.elapsedRealtime() - last >= STALE_MS) syncSoon()
    }

    /** Redovno preuzimanje sa naloga, da se vidi i ono što je gledano drugde. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<TraktSyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK)
    }

    private companion object {
        const val PERIODIC_HOURS = 6L
        const val STALE_MS = 5 * 60 * 1000L
        const val BACKOFF_SECONDS = 30L
        const val PERIODIC_WORK = "trakt-sync-periodic"
        const val ONE_TIME_WORK = "trakt-sync"
    }
}
