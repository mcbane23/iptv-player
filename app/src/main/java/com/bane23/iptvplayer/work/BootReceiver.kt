package com.bane23.iptvplayer.work

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bane23.iptvplayer.MainActivity
import com.bane23.iptvplayer.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Pokreće aplikaciju kad se uređaj upali, ako je to uključeno u podešavanjima (za TV box). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settings: SettingsStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        // Čitanje jedne vrednosti iz DataStore-a je kratko, pa ne treba pozadinski posao.
        if (!runBlocking { settings.autostart.first() }) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: RuntimeException) {
            // Sistem nije dozvolio pokretanje iz pozadine; korisnik otvara aplikaciju sam.
        }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON")
    }
}

/** Dozvola bez koje noviji Android ne da aplikaciji da se sama otvori posle paljenja uređaja. */
object Autostart {
    /**
     * Od Androida 10 aplikacija sme da otvori svoj ekran iz pozadine samo ako ima dozvolu
     * „Prikaz preko drugih aplikacija”. Na starijim verzijama dozvola nije potrebna.
     */
    fun isAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

    /** Otvara sistemski ekran za tu dozvolu. Vraća false ako ga uređaj nema. */
    fun openPermissionScreen(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // probati sledeći oblik
            } catch (_: SecurityException) {
                // probati sledeći oblik
            }
        }
        return false
    }
}
