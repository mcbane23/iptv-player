package com.bane23.iptvplayer.data.saver

import android.content.Context
import com.bane23.iptvplayer.data.download.NetworkKind
import com.bane23.iptvplayer.data.download.NetworkWatcher
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.ui.common.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Štednja mobilnih podataka: važi na telefonu, dok je uključena u Podešavanjima i dok telefon izlazi
 * na internet preko mobilne mreže. TV je nema, jer ne ume pouzdano da zna da je iza mobilne mreže
 * (deljena veza sa telefona mu izgleda kao Wi-Fi).
 */
@Singleton
class DataSaver @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsStore,
    network: NetworkWatcher,
) {
    private val phone = !DeviceType.isTv(context)

    /** Da li štednja sada važi. */
    val active: Flow<Boolean> = if (!phone) {
        flowOf(false)
    } else {
        combine(settings.dataSaver, network.kind) { enabled, kind -> enabled && kind == NetworkKind.MOBILE }.distinctUntilChanged()
    }

    suspend fun isActive(): Boolean = phone && active.first()
}
