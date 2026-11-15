package com.bane23.iptvplayer.domain

/** Greška pri radu sa listom, u obliku koji UI može da prevede u razumljivu poruku. */
data class PlaylistError(val kind: Kind, val httpCode: Int? = null) {
    enum class Kind {
        MISSING_FIELDS,
        INVALID_URL,
        NO_NETWORK,
        TIMEOUT,
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
    constructor(kind: PlaylistError.Kind, cause: Throwable? = null) : this(PlaylistError(kind), cause)
}
