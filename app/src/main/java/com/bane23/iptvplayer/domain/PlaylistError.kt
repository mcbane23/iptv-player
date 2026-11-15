package com.bane23.iptvplayer.domain

/** Greška pri radu sa listom, u obliku koji UI može da prevede u razumljivu poruku. */
data class PlaylistError(
    val kind: Kind,
    val httpCode: Int? = null,
    /** Tehnički opis greške (vrsta izuzetka i poruka), prikazuje se ispod razumljive poruke. */
    val detail: String? = null,
) {
    enum class Kind {
        MISSING_FIELDS,
        INVALID_URL,
        NO_NETWORK,
        TIMEOUT,
        HOST_NOT_FOUND,
        SSL_ERROR,
        HTTP_ERROR,
        EMPTY_PLAYLIST,
        FILE_UNREADABLE,
        AUTH_FAILED,
        ACCOUNT_EXPIRED,
        ACCOUNT_DISABLED,
        BAD_RESPONSE,
        CREDENTIALS_UNREADABLE,
        UNKNOWN,
    }
}

class PlaylistException(val error: PlaylistError, cause: Throwable? = null) :
    Exception(error.kind.name, cause) {
    constructor(kind: PlaylistError.Kind, cause: Throwable? = null) :
        this(PlaylistError(kind, detail = cause?.let(::describe)), cause)

    companion object {
        /** Kratak tehnički opis izuzetka, npr. `ConnectException: Failed to connect`. */
        fun describe(e: Throwable): String {
            val root = generateSequence(e) { it.cause }.last()
            val text = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
            return if (root === e) text else "$text (${root.javaClass.simpleName}: ${root.message.orEmpty()})"
        }
    }
}
