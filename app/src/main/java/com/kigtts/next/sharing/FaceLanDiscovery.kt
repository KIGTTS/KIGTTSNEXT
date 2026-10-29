package com.kigtts.next.sharing

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

internal data class FaceLanIdentity(val id: String, val name: String, val port: Int, val token: String)

/** Both sending and receiving pages advertise; accepting a transfer still requires confirmation. */
internal class FaceLanDiscovery(context: Context, private val found: (FacePeer) -> Unit, private val lost: (String) -> Unit) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var identity: FaceLanIdentity? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var watcher: ConnectivityManager.NetworkCallback? = null
    private var epoch = 0
    private val sources = mutableMapOf<String, MutableMap<String, FacePeer>>()
    private val nsd = FaceNsdDiscovery(context, { seen("nsd", it) }, { absent("nsd", it) })
    private var beacon: FaceLanBeacon? = null
    private val restart = Runnable { identity?.let { clearPeers(); restartTransports(it) } }

    fun start(id: String, name: String, port: Int, token: String) {
        val next = FaceLanIdentity(id, name.take(40), port, token)
        if (identity == next) return
        stop(); identity = next
        multicast = runCatching {
            context.getSystemService(WifiManager::class.java).createMulticastLock("KIGTTS facing discovery")
                .apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        restartTransports(next)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = changed()
            override fun onLost(network: Network) = changed()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = changed()
            private fun changed() { handler.post {
                if (watcher === this && identity != null) {
                    handler.removeCallbacks(restart); handler.postDelayed(restart, 750)
                }
            } }
        }
        watcher = callback
        runCatching { connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback) }
    }
    private fun restartTransports(value: FaceLanIdentity) {
        val generation = ++epoch
        nsd.start(value)
        beacon?.close()
        beacon = FaceLanBeacon(value, { faceLanBroadcasts(context) }, { peer -> handler.post {
            if (generation == epoch && identity != null) seen("udp", peer)
        } }, { id -> handler.post { if (generation == epoch) absent("udp", id) } }).also { it.start() }
    }
    private fun seen(source: String, peer: FacePeer) {
        if (identity == null) return
        sources.getOrPut(peer.id) { mutableMapOf() }[source] = peer
        found(sources.getValue(peer.id).values.firstOrNull { it.host?.contains(':') == false } ?: peer)
    }
    private fun absent(source: String, id: String) {
        val peers = sources[id] ?: return
        peers.remove(source)
        if (peers.isEmpty()) { sources.remove(id); lost(id) } else found(peers.values.first())
    }
    private fun clearPeers() { val ids = sources.keys.toList(); sources.clear(); ids.forEach(lost) }
    fun stop() {
        identity = null; epoch++
        handler.removeCallbacks(restart)
        watcher?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }; watcher = null
        nsd.stop(); beacon?.close(); beacon = null
        multicast?.let { runCatching { if (it.isHeld) it.release() } }; multicast = null
        clearPeers()
    }
    companion object { const val TYPE = "_kigtts-photo._tcp." }
}
