package com.chattlyx.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Transport classification used by adaptive policies (Section 6.5). */
enum class NetworkType { WIFI, CELLULAR, ETHERNET, UNKNOWN, NONE }

data class NetworkStatus(
    val available: Boolean,
    val validated: Boolean,
    val metered: Boolean,
    val roaming: Boolean,
    val type: NetworkType,
) {
    val isUsable: Boolean
        get() = available && validated
}

interface ConnectivityMonitor {
    /** Emits the current status immediately, then on every change. */
    val status: Flow<NetworkStatus>

    fun snapshot(): NetworkStatus
}

/**
 * Live connectivity monitor backed by [ConnectivityManager.NetworkCallback].
 * Powers the "Waiting for network…" banner, transport fallback decisions and
 * instant WebSocket reconnects on network change.
 */
@Singleton
class ConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    private val manager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val status: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(readStatus())
            }

            override fun onLost(network: Network) {
                trySend(readStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(readStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)
        trySend(readStatus())

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override fun snapshot(): NetworkStatus = readStatus()

    private fun readStatus(): NetworkStatus {
        val network = manager.activeNetwork ?: return NetworkStatus(false, false, false, false, NetworkType.NONE)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return NetworkStatus(false, false, false, false, NetworkType.NONE)

        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }

        return NetworkStatus(
            available = true,
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING).not(),
            type = type,
        )
    }
}
