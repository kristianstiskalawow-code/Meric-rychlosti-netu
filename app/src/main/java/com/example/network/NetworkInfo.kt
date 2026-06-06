package com.example.network

data class NetworkInfo(
    val type: NetworkType = NetworkType.NONE,
    val name: String = "Odpojeno", // SSID or Operator name or fallback
    val signalLevel: Int = 0, // 0 to 4 bars
    val signalDbm: Int? = null, // raw signal strength in dBm if available
    val linkSpeedMbps: Int? = null, // wifi link speed or estimated bandwidth
    val frequencyGhz: Double? = null, // Wi-Fi frequency (e.g., 2.4, 5.0, 6.0)
    val ipAddress: String? = null, // local IP address
    val detailString: String = "Žádné aktivní připojení"
)

enum class NetworkType {
    NONE, WIFI, MOBILE
}
