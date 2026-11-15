package com.bane23.iptvplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistError.Kind
import com.bane23.iptvplayer.domain.PlaylistType
import java.text.DateFormat
import java.util.Date

/** Poruka o grešci razumljiva korisniku. */
@Composable
fun PlaylistError.message(): String = when (kind) {
    Kind.MISSING_FIELDS -> stringResource(R.string.error_missing_fields)
    Kind.INVALID_URL -> stringResource(R.string.error_invalid_url)
    Kind.NO_NETWORK -> stringResource(R.string.error_no_network)
    Kind.TIMEOUT -> stringResource(R.string.error_timeout)
    Kind.HTTP_ERROR -> stringResource(R.string.error_http, httpCode ?: 0)
    Kind.EMPTY_PLAYLIST -> stringResource(R.string.error_empty_playlist)
    Kind.FILE_UNREADABLE -> stringResource(R.string.error_file_unreadable)
    Kind.AUTH_FAILED -> stringResource(R.string.error_auth_failed)
    Kind.ACCOUNT_EXPIRED -> stringResource(R.string.error_account_expired)
    Kind.ACCOUNT_DISABLED -> stringResource(R.string.error_account_disabled)
    Kind.BAD_RESPONSE -> stringResource(R.string.error_bad_response)
    Kind.CREDENTIALS_UNREADABLE -> stringResource(R.string.error_credentials_unreadable)
    Kind.UNKNOWN -> stringResource(R.string.error_unknown)
}

@Composable
fun PlaylistType.label(): String = when (this) {
    PlaylistType.M3U_URL -> stringResource(R.string.playlist_type_m3u_url)
    PlaylistType.M3U_FILE -> stringResource(R.string.playlist_type_m3u_file)
    PlaylistType.XTREAM -> stringResource(R.string.playlist_type_xtream)
}

/** Drugi red u spisku lista: tip, broj kanala, vreme poslednjeg osvežavanja. */
@Composable
fun Playlist.summary(): String {
    val channels = pluralStringResource(R.plurals.channel_count, channelCount, channelCount)
    val refreshed = lastRefreshedAt?.let {
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        stringResource(R.string.playlist_refreshed_at, time)
    }
    return listOfNotNull(type.label(), channels, refreshed).joinToString(" · ")
}
