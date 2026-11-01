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
    private val mutableStarting = MutableStateFlow(false); val starting = mutableStarting.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null
    @SuppressLint("MissingPermission")
    fun start() {
        if (mutableStarting.value || reservation != null) return
        mutableStarting.value = true
        val epoch = ++generation
        timeout = Runnable { if (epoch == generation && mutableStarting.value) {
            stop(); error("热点创建超时，请重试或使用附近直连")
        } }.also { handler.postDelayed(it, 20_000) }
        try {
            context.getSystemService(WifiManager::class.java).startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    if (epoch != generation) { value.close(); return }
                    finishStarting()
                    reservation = value
                    val ssid: String; val password: String
                    if (Build.VERSION.SDK_INT >= 30) { ssid = value.softApConfiguration.ssid.orEmpty(); password = value.softApConfiguration.passphrase.orEmpty() }
                    else { @Suppress("DEPRECATION") val config = value.wifiConfiguration; ssid = config?.SSID.orEmpty().trim('"'); password = config?.preSharedKey.orEmpty().trim('"') }
                    mutable.value = HotspotDetails(ssid, password)
                }
                override fun onStopped() { if (epoch == generation) { finishStarting(); reservation = null; mutable.value = null } }
                override fun onFailed(reason: Int) { if (epoch == generation) { finishStarting(); error("热点未能开启，请确认 Wi-Fi 已打开，或改用附近直连") } }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) { finishStarting(); error("热点未能开启，请检查附近设备权限和 Wi-Fi 开关") }
    }
    private fun finishStarting() { mutableStarting.value = false; timeout?.let(handler::removeCallbacks); timeout = null }
    fun stop() { generation++; finishStarting(); reservation?.close(); reservation = null; mutable.value = null }
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
