package com.bane23.iptvplayer.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/** Jezik aplikacije: kao na uređaju ili izričito izabran u podešavanjima. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),

    // Latinica se navodi izričito: tekstovi su na latinici, a i datumi tada izlaze na latinici.
    SERBIAN("sr-Latn"),
    ENGLISH("en"),
}

/**
 * Čuva izabrani jezik i primenjuje ga na kontekst. Izbor se čita pre nego što se aktivnost napravi
 * (u attachBaseContext), pa stoji u SharedPreferences, koje se čitaju odmah, a ne u DataStore-u.
 */
object AppLocale {
    private const val PREFS = "app_language"
    private const val KEY = "language"

    /** Jezik uređaja, zapamćen pre prve izmene podrazumevanog, za povratak na „Kao uređaj”. */
    private val systemLocale: Locale = Locale.getDefault()

    fun get(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.name == saved } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) {
        // commit, ne apply: aktivnost se odmah zatim pravi iznova i mora da pročita novu vrednost.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).commit()
    }

    /** Kontekst sa izabranim jezikom; za „Kao uređaj” vraća isti kontekst. */
    fun wrap(base: Context): Context {
        val tag = get(base).tag
        if (tag == null) {
            Locale.setDefault(systemLocale)
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        // Nazivi jezika i datumi koji se prave van resursa prate isti jezik.
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Menja jezik i iznova pravi aktivnost, da se svi tekstovi odmah vide na novom jeziku. */
    fun change(context: Context, language: AppLanguage) {
        if (get(context) == language) return
        set(context, language)
        context.findActivity()?.recreate()
    }

    /** Pravi aktivnost iznova kad je jezik promenjen mimo [change] (uvoz podešavanja iz fajla). */
    fun recreate(context: Context) {
        context.findActivity()?.recreate()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
