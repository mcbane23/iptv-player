package com.bane23.iptvplayer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bane23.iptvplayer.data.repo.EpgRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Učitava TV vodič jedne liste (kad je zadat njen ID) ili svih lista kojima je vodič zastareo. */
class EpgRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun epgRepository(): EpgRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .epgRepository()
        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, NO_PLAYLIST)
        if (playlistId == NO_PLAYLIST) repository.refreshStale() else repository.refresh(playlistId)
        // Neuspeh se vidi na ekranu vodiča; sledeći redovni pokušaj je za nekoliko sati.
        return Result.success()
    }

    companion object {
        const val KEY_PLAYLIST_ID = "playlistId"
        private const val NO_PLAYLIST = -1L
    }
}
