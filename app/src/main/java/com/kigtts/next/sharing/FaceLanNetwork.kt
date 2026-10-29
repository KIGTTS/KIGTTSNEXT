package com.kigtts.next.sharing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

internal fun faceLanBroadcast(address: InetAddress, prefix: Int): InetAddress? {
    if (address !is Inet4Address || !address.isSiteLocalAddress || prefix !in 1..30) return null
    val bits = address.address.fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 255) }
    val broadcast = bits or (-1 ushr prefix)
    return InetAddress.getByAddress(ByteArray(4) { (broadcast ushr (24 - 8 * it)).toByte() })
}

/** Include Wi-Fi without internet, and tether/P2P interfaces; never enumerate public or VPN subnets. */
internal fun faceLanBroadcasts(context: Context): List<InetSocketAddress> {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val wifi = connectivity.allNetworks.filter {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty() }
        .mapNotNull { faceLanBroadcast(it.address, it.prefixLength) }
    val tether = runCatching { NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("p2p")) }
        .flatMap { it.interfaceAddresses }.mapNotNull { faceLanBroadcast(it.address, it.networkPrefixLength.toInt()) }
    }.getOrDefault(emptyList())
    val addresses = (wifi + tether).distinct()
    return (addresses + if (addresses.isNotEmpty()) listOf(InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))) else emptyList())
        .distinct().map { InetSocketAddress(it, FACE_LAN_PORT) }
}
