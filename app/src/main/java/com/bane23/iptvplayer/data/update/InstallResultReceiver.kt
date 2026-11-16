package com.bane23.iptvplayer.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Prima rezultat instalacije od sistema i, ako treba, otvara ekran za potvrdu „Instaliraj”. */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject lateinit var updater: AppUpdater

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    updater.onInstallFailed()
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }.onFailure { updater.onInstallFailed() }
            }
            // Posle uspešne instalacije sistem gasi aplikaciju; ovde ništa ne treba.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onInstallCancelled()
            else -> updater.onInstallFailed()
        }
    }
}
