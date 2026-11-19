package com.bane23.iptvplayer.domain

/** Dugme daljinskog kome korisnik sam bira šta radi u plejeru kanala na TV-u. */
enum class RemoteButton {
    RED,
    GREEN,
    YELLOW,
    BLUE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    MENU,

    /** Dugo držanje dugmeta OK. */
    LONG_OK,
    ;

    val isColor: Boolean get() = this == RED || this == GREEN || this == YELLOW || this == BLUE

    /** Šta dugme radi dok ga korisnik ne podesi. */
    val defaultAction: RemoteAction
        get() = when (this) {
            RED -> RemoteAction.LAST_CHANNEL
            GREEN -> RemoteAction.SCHEDULE
            YELLOW -> RemoteAction.FAVORITE
            BLUE -> RemoteAction.AUDIO
            CHANNEL_UP -> RemoteAction.NEXT_CHANNEL
            CHANNEL_DOWN -> RemoteAction.PREVIOUS_CHANNEL
            MENU -> RemoteAction.QUICK_SETTINGS
            LONG_OK -> RemoteAction.CHANNEL_LIST
        }

    companion object {
        // Brojevi tastera iz android.view.KeyEvent; ovde su ispisani da pravila ostanu čist Kotlin.
        private const val KEY_MENU = 82
        private const val KEY_CHANNEL_UP = 166
        private const val KEY_CHANNEL_DOWN = 167
        private const val KEY_RED = 183
        private const val KEY_GREEN = 184
        private const val KEY_YELLOW = 185
        private const val KEY_BLUE = 186

        /** Dugme za taster sa daljinskog, ili null kad taster nije među onima koji se podešavaju. */
        fun ofKey(keyCode: Int): RemoteButton? = when (keyCode) {
            KEY_RED -> RED
            KEY_GREEN -> GREEN
            KEY_YELLOW -> YELLOW
            KEY_BLUE -> BLUE
            KEY_CHANNEL_UP -> CHANNEL_UP
            KEY_CHANNEL_DOWN -> CHANNEL_DOWN
            KEY_MENU -> MENU
            else -> null
        }
    }
}

/** Šta dugme daljinskog može da radi u plejeru kanala. */
enum class RemoteAction {
    NONE,

    /** Kanal koji se gledao pre ovog. */
    LAST_CHANNEL,
    NEXT_CHANNEL,
    PREVIOUS_CHANNEL,
    CHANNEL_LIST,

    /** Program (raspored) kanala koji se gleda. */
    SCHEDULE,
    FAVORITE,
    AUDIO,
    SUBTITLES,
    QUICK_SETTINGS,

    /** Informacije o strimu. */
    INFO,

    /** Kontrole plejera, kao na OK. */
    CONTROLS,
    ;

    /** Radnja se ponavlja dok se dugme drži (prelazak sa kanala na kanal). */
    val repeats: Boolean get() = this == NEXT_CHANNEL || this == PREVIOUS_CHANNEL

    companion object {
        /** Sačuvana radnja za [button]; nepoznata ili nepodešena vrednost daje podrazumevanu. */
        fun of(saved: String?, button: RemoteButton): RemoteAction =
            entries.firstOrNull { it.name == saved } ?: button.defaultAction
    }
}

/** Dugo držanje OK: toliko mora da prođe od pritiska. */
const val REMOTE_LONG_PRESS_MS = 500L
