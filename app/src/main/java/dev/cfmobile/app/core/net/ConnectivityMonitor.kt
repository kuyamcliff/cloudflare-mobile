package dev.cfmobile.app.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionState(val online: Boolean, val metered: Boolean, val wifi: Boolean)

/** Current network state from the platform callback; no polling (spec 186, 187). */
class ConnectivityMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(current())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    init {
        runCatching {
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _state.value = current() }
                override fun onLost(network: Network) { _state.value = current() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _state.value = current() }
            })
        }
    }

    fun current(): ConnectionState {
        val caps = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            ?: return ConnectionState(online = false, metered = false, wifi = false)
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return ConnectionState(online, metered, wifi)
    }
}
