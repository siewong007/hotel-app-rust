package com.hotelapp.staff.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(computeIsOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateState()
        }

        override fun onLost(network: Network) {
            updateState()
        }

        override fun onUnavailable() {
            _isOnline.value = false
        }
    }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        }
    }

    private fun updateState() {
        _isOnline.value = computeIsOnline()
    }

    private fun computeIsOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
