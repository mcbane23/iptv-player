package com.bane23.iptvplayer

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import android.app.Application
import android.content.Context
import com.bane23.iptvplayer.data.crash.CrashLog
import com.bane23.iptvplayer.data.download.DownloadRepository
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.reminder.ReminderRepository
import com.bane23.iptvplayer.data.watchnext.WatchNextSync
import com.bane23.iptvplayer.ui.common.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bane23.iptvplayer.data.trakt.TraktTokenStore
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.work.EpgScheduler
import com.bane23.iptvplayer.work.TraktScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IptvApplication : Application(), SingletonImageLoader.Factory {

    /** Logoi i posteri se meko pojave kad stignu, umesto da iskoče u punoj jačini. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(IMAGE_FADE_MS).build()


    @Inject
    lateinit var epgScheduler: EpgScheduler

    @Inject
    lateinit var traktScheduler: TraktScheduler

    @Inject
    lateinit var traktTokens: TraktTokenStore

    /** Čim se napravi, vraća budilnike zakazanih snimanja (ne preživljavaju gašenje uređaja). */
    @Inject
    lateinit var recordings: RecordingRepository

    /** Čim se napravi, nastavlja preuzimanja koja su ostala u redu. */
    @Inject
    lateinit var downloads: DownloadRepository

    @Inject
    lateinit var reminders: ReminderRepository

    @Inject
    lateinit var watchNext: WatchNextSync

    @Inject
    lateinit var channelRules: com.bane23.iptvplayer.data.repo.ChannelRulesSync

    /** Jezik izabran u podešavanjima važi i za tekstove van aktivnosti (obaveštenja, kratke poruke). */
    override fun attachBaseContext(base: Context) {
        // Pre svega ostalog, da se zabeleži i pad pri samom pokretanju.
        CrashLog.install(base)
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        epgScheduler.schedulePeriodic()
        epgScheduler.schedulePlaylistRefresh()
        // Pravila za kanale (SD kad postoji HD, kanali +1, istoimeni) prate podešavanja i osvežavanje lista.
        channelRules.start()
        // Budilnici podsetnika ne preživljavaju gašenje uređaja.
        CoroutineScope(Dispatchers.IO).launch { reminders.restoreAlarms() }
        // Pri svakom pokretanju preuzima se ono što je na Trakt nalogu gledano drugde.
        // Bez prijave posao se odmah završava.
        if (traktTokens.isAvailable) traktScheduler.syncSoon()
        // Red „Nastavite sa gledanjem” na početnom ekranu postoji samo na TV uređajima.
        if (DeviceType.isTv(this)) watchNext.start()
    }

    private companion object {
        const val IMAGE_FADE_MS = 200
    }
}
