package com.bane23.iptvplayer

import android.app.Application
import com.bane23.iptvplayer.work.EpgScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IptvApplication : Application() {

    @Inject
    lateinit var epgScheduler: EpgScheduler

    override fun onCreate() {
        super.onCreate()
        epgScheduler.schedulePeriodic()
    }
}
