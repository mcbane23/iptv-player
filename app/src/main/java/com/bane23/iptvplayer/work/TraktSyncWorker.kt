package com.bane23.iptvplayer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.data.trakt.TraktSyncResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Šalje promene na Trakt i preuzima ono što je na nalogu označeno kao odgledano. */
class TraktSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun traktRepository(): TraktRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .traktRepository()
        return when (repository.sync(automatic = true)) {
            // Mreža ili Trakt trenutno ne rade: WorkManager pokušava ponovo, sve ređe.
            TraktSyncResult.FAILED -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
