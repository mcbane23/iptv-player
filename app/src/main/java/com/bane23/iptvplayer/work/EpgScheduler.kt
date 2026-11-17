package com.bane23.iptvplayer.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Zakazuje učitavanje TV vodiča u pozadini (WorkManager). */
@Singleton
class EpgScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Redovno osvežavanje vodiča svih lista i brisanje starih emisija. Vodič je veliki fajl, pa se
     * u pozadini ne preuzima dok je baterija pri kraju; ručno osvežavanje radi uvek.
     */
    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(REFRESH_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        // UPDATE menja uslove već zakazanog posla, a ne pomera vreme sledećeg pokretanja.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Učitava vodič jedne liste što pre (posle dodavanja ili osvežavanja liste, ili na zahtev). */
    fun refreshNow(playlistId: Long) {
        val request = OneTimeWorkRequestBuilder<EpgRefreshWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(EpgRefreshWorker.KEY_PLAYLIST_ID to playlistId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("$ONE_TIME_WORK$playlistId", ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val REFRESH_HOURS = 12L
        const val PERIODIC_WORK = "epg-refresh"
        const val ONE_TIME_WORK = "epg-refresh-"
    }
}
