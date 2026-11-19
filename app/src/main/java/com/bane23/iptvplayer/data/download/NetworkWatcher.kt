package com.bane23.iptvplayer.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Prati preko koje mreže uređaj izlazi na internet, da preuzimanje ne troši mobilne podatke. */
@Singleton
class NetworkWatcher @Inject constructor(@ApplicationContext private val context: Context) {

    val kind: Flow<NetworkKind> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            // Uređaj ne ume da kaže na kojoj je mreži: preuzimanje se ne zadržava.
            trySend(NetworkKind.WIFI)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(kindOf(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(NetworkKind.NONE)
            }
        }
        val registered = try {
            trySend(kindOf(manager.getNetworkCapabilities(manager.activeNetwork)))
            manager.registerDefaultNetworkCallback(callback)
            true
        } catch (_: RuntimeException) {
            trySend(NetworkKind.WIFI)
            false
        }
        awaitClose { if (registered) manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun kindOf(capabilities: NetworkCapabilities?): NetworkKind = when {
        capabilities == null -> NetworkKind.NONE
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
        // Ostalo (npr. VPN bez podatka o mreži ispod): odlučuje da li se promet naplaćuje.
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.WIFI
        else -> NetworkKind.MOBILE
    }
}
