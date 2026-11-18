package com.bane23.iptvplayer.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.bane23.iptvplayer.MainActivity
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.work.Autostart
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Ponovno otvaranje aplikacije posle ažuriranja iz same aplikacije. Sistem pri instalaciji gasi
 * aplikaciju, pa se pre instalacije ostavlja beleška, a nova verzija je čita kad joj sistem javi
 * da je instalirana.
 */
object UpdateRelaunch {
    private const val PREFS = "update_relaunch"
    private const val KEY_STARTED_AT = "started_at"

    /** Posle ovoliko vremena beleška više ne važi (instalacija je odavno gotova ili je propala). */
    private const val VALID_MS = 10 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Instalacija upravo kreće iz aplikacije. Upis je odmah na disku, jer se proces uskoro gasi. */
    fun mark(context: Context) {
        prefs(context).edit().putLong(KEY_STARTED_AT, System.currentTimeMillis()).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_STARTED_AT).apply()
    }

    /** Da li je ažuriranje malopre pokrenuto iz aplikacije. Beleška se troši jednim čitanjem. */
    fun consume(context: Context): Boolean {
        val prefs = prefs(context)
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        if (startedAt == 0L) return false
        prefs.edit().remove(KEY_STARTED_AT).commit()
        val age = System.currentTimeMillis() - startedAt
        return age in 0..VALID_MS
    }

    /**
     * Da li sistem dozvoljava aplikaciji da se sama otvori iz pozadine. Do Androida 9 uvek, a od
     * Androida 10 samo uz dozvolu „Prikaz preko drugih aplikacija” (ista kao za pokretanje pri paljenju).
     */
    fun canReopen(context: Context): Boolean = Autostart.isAllowed(context)
}

/** Sistem javlja novoj verziji da je instalirana: potvrda korisniku i, gde sistem dozvoli, ponovno otvaranje. */
@AndroidEntryPoint
class UpdateFinishedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settings: SettingsStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Ažuriranje spolja (ručna instalacija APK-a) ne otvara aplikaciju.
        if (!UpdateRelaunch.consume(context)) return
        // Čitanje jedne vrednosti iz DataStore-a je kratko, pa ne treba pozadinski posao.
        val canReopen = UpdateRelaunch.canReopen(context) && runBlocking { settings.reopenAfterUpdate.first() }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        val localized = AppLocale.wrap(context)
        val text = localized.getString(R.string.update_done, version)
        runCatching { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
        if (!canReopen) return
        val pm = context.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: pm.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            // Sistem nije dozvolio pokretanje iz pozadine; korisnik otvara aplikaciju sam.
        }
    }
}
