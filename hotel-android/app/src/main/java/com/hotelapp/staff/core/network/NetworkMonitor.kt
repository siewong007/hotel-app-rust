package com.hotelapp.staff.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.hotelapp.staff.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val lock = Any()
    private val validNetworks = HashSet<Network>()

    private val _isOnline = MutableStateFlow(computeInitialOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network)
            updateNetwork(network, caps)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateNetwork(network, networkCapabilities)
        }

        override fun onLost(network: Network) {
            synchronized(lock) {
                validNetworks.remove(network)
                _isOnline.value = validNetworks.isNotEmpty()
            }
        }

        override fun onUnavailable() {
            synchronized(lock) {
                validNetworks.clear()
                _isOnline.value = false
            }
        }
    }

    init {
        val registered = runCatching {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        }.isSuccess
        if (registered) {
            synchronized(lock) {
                validNetworks.clear()
                val cm = connectivityManager
                val active = cm?.activeNetwork
                val caps = if (active != null) cm.getNetworkCapabilities(active) else null
                if (active != null && hasUsableInternet(caps)) {
                    validNetworks.add(active)
                }
                _isOnline.value = validNetworks.isNotEmpty()
            }
        }
    }

    private fun hasUsableInternet(capabilities: NetworkCapabilities?): Boolean {
        if (capabilities == null) return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return if (BuildConfig.DEBUG) {
            true
        } else {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    private fun updateNetwork(network: Network, capabilities: NetworkCapabilities?) {
        val usable = hasUsableInternet(capabilities)
        synchronized(lock) {
            if (usable) {
                validNetworks.add(network)
            } else {
                validNetworks.remove(network)
            }
            _isOnline.value = validNetworks.isNotEmpty()
        }
    }

    private fun computeInitialOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return hasUsableInternet(caps)
    }
}
