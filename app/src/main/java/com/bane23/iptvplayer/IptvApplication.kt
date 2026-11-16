package com.bane23.iptvplayer

import android.app.Application
import android.content.Context
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.trakt.TraktTokenStore
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.work.EpgScheduler
import com.bane23.iptvplayer.work.TraktScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IptvApplication : Application() {

    @Inject
    lateinit var epgScheduler: EpgScheduler

    @Inject
    lateinit var traktScheduler: TraktScheduler

    @Inject
    lateinit var traktTokens: TraktTokenStore

    /** Čim se napravi, vraća budilnike zakazanih snimanja (ne preživljavaju gašenje uređaja). */
    @Inject
    lateinit var recordings: RecordingRepository

    /** Jezik izabran u podešavanjima važi i za tekstove van aktivnosti (obaveštenja, kratke poruke). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        epgScheduler.schedulePeriodic()
        // Pri svakom pokretanju preuzima se ono što je na Trakt nalogu gledano drugde.
        // Bez prijave posao se odmah završava.
        if (traktTokens.isAvailable) traktScheduler.syncSoon()
    }
}
