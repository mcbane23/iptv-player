package com.bane23.iptvplayer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.PlaylistRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** Jednom dnevno ponovo preuzima liste (osim onih iz fajla), ako to nije isključeno u podešavanjima. */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun playlistRepository(): PlaylistRepository
        fun settingsStore(): SettingsStore
    }

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        if (dependencies.settingsStore().autoRefreshPlaylists.first()) {
            dependencies.playlistRepository().refreshStale(STALE_AFTER_MS)
        }
        // Neuspeh se ne ponavlja odmah; sledeći pokušaj je sutradan.
        return Result.success()
    }

    private companion object {
        /** Lista osvežena u poslednjih 20 sati (npr. ručno) se preskače. */
        const val STALE_AFTER_MS = 20L * 60 * 60 * 1000
    }
}
