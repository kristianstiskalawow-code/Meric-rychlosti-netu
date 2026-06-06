package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    init {
        updateNetworkInfo()
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkInfo()
                }

                override fun onLost(network: Network) {
                    updateNetworkInfo()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateNetworkInfo()
                }
            })
        } catch (e: Exception) {
            // Safe fallback if permission is tricky or other issues
        }
    }

    fun updateNetworkInfo() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        if (capabilities == null) {
            _networkInfo.value = NetworkInfo()
            return
        }

        val ipAddress = getLocalIpAddress()

        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wifiInfo = wifiManager?.connectionInfo
                val rssi = wifiInfo?.rssi ?: -100
                val level = calculateWifiSignalLevel(rssi)
                
                var ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Wi-Fi Síť"
                if (ssid == "<unknown ssid>" || ssid.isBlank()) {
                    ssid = "Připojená Wi-Fi"
                }

                val linkSpeed = wifiInfo?.linkSpeed ?: -1
                val linkSpeedStr = if (linkSpeed > 0) "$linkSpeed Mbps" else null
                
                val frequency = wifiInfo?.frequency ?: -1
                val freqGhz = if (frequency > 0) frequency / 1000.0 else null

                val signalStrengthDbm = if (rssi != -127) rssi else null

                _networkInfo.value = NetworkInfo(
                    type = NetworkType.WIFI,
                    name = ssid,
                    signalLevel = level,
                    signalDbm = signalStrengthDbm,
                    linkSpeedMbps = if (linkSpeed > 0) linkSpeed else null,
                    frequencyGhz = freqGhz,
                    ipAddress = ipAddress,
                    detailString = "Wi-Fi: $ssid | Rychlost: ${linkSpeedStr ?: "Neznámo"} | Frekvence: ${freqGhz?.let { "$it GHz" } ?: "Neznámo"}"
                )
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val carrierName = telephonyManager?.networkOperatorName ?: "Mobilní operátor"
                
                // Read signal strength on newer Android devices using Capabilities if possible
                var signalLevel = 2 // average fallback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Modern cellular info can be quite detailed, but requires high level permissions.
                    // Fallback to reading from network capabilities if signalStrength is populated
                }

                _networkInfo.value = NetworkInfo(
                    type = NetworkType.MOBILE,
                    name = carrierName,
                    signalLevel = signalLevel,
                    ipAddress = ipAddress,
                    detailString = "Mobilní síť ($carrierName)"
                )
            }
            else -> {
                _networkInfo.value = NetworkInfo(
                    type = NetworkType.NONE,
                    name = "Ostatní připojení",
                    signalLevel = 3,
                    ipAddress = ipAddress,
                    detailString = "Kabelové nebo VPN připojení"
                )
            }
        }
    }

    private fun calculateWifiSignalLevel(rssi: Int): Int {
        return when {
            rssi >= -50 -> 4
            rssi >= -65 -> 3
            rssi >= -80 -> 2
            rssi >= -90 -> 1
            else -> 0
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            // Fail silently
        }
        return null
    }
}
