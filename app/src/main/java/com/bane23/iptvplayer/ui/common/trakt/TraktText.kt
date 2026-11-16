package com.bane23.iptvplayer.ui.common.trakt

import java.text.DateFormat
import java.util.Date

/** Vreme poslednje sinhronizacije, kratko i po jeziku uređaja. */
fun formatSyncTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
