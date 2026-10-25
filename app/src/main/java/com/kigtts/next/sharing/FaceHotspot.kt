package com.kigtts.next.sharing

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

internal data class HotspotDetails(val ssid: String, val password: String) {
    fun qr(): String {
        fun escape(value: String) = value.replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:").replace(",", "\\,").replace("\"", "\\\"")
        return "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};;"
    }
}
internal class FaceHotspot(private val context: Context, private val error: (String) -> Unit) {
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private val mutable = MutableStateFlow<HotspotDetails?>(null); val details = mutable.asStateFlow()
    private var generation = 0
    private var starting = false
    @SuppressLint("MissingPermission")
    fun start() {
        if (starting || reservation != null) return
        starting = true
        val epoch = ++generation
        try {
            context.getSystemService(WifiManager::class.java).startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    if (epoch != generation) { value.close(); return }
                    starting = false
                    reservation = value
                    val ssid: String; val password: String
                    if (Build.VERSION.SDK_INT >= 30) { ssid = value.softApConfiguration.ssid.orEmpty(); password = value.softApConfiguration.passphrase.orEmpty() }
                    else { @Suppress("DEPRECATION") val config = value.wifiConfiguration; ssid = config?.SSID.orEmpty().trim('"'); password = config?.preSharedKey.orEmpty().trim('"') }
                    mutable.value = HotspotDetails(ssid, password)
                }
                override fun onStopped() { if (epoch == generation) { reservation = null; mutable.value = null } }
                override fun onFailed(reason: Int) { if (epoch == generation) { starting = false; error("热点未能开启，请确认 Wi-Fi 已打开，或改用同一 Wi-Fi") } }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) { starting = false; error("热点未能开启，请检查附近设备权限和 Wi-Fi 开关") }
    }
    fun stop() { generation++; starting = false; reservation?.close(); reservation = null; mutable.value = null }
}

internal fun localWifiAddresses(context: Context): List<String> {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val wifi = connectivity.allNetworks.filter { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        .flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty() }.map { it.address }
        .filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") }
        .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress } }.getOrDefault(emptyList())
    return (wifi + interfaces).distinct()
}
