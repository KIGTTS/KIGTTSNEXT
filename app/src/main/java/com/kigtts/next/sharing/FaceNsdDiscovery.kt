package com.kigtts.next.sharing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

/** Recover failed registrations/browses instead of silently searching forever. All state is on main. */
internal class FaceNsdDiscovery(context: Context, private val found: (FacePeer) -> Unit, private val lost: (String) -> Unit) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var identity: FaceLanIdentity? = null
    private var epoch = 0
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolved = mutableMapOf<String, String>()
    private val resolver = FaceNsdResolver(manager, handler) { info ->
        val self = identity
        if (self != null) faceNsdPeer(info, self.id)?.let { peer ->
            resolved[faceNsdKey(info)] = peer.id; found(peer)
        }
    }
    private val registerAgain = Runnable { identity?.let(::register) }
    private val browseAgain = Runnable { if (identity != null) browse() }

    fun start(value: FaceLanIdentity) { stop(); identity = value; register(value); browse() }

    private fun register(value: FaceLanIdentity) {
        val generation = epoch
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = failed()
            fun failed() { handler.post {
                if (generation == epoch && registration === this) {
                    registration = null
                    runCatching { manager.unregisterService(this) }
                    handler.removeCallbacks(registerAgain); handler.postDelayed(registerAgain, 2_000)
                }
            } }
        }
        registration = listener
        runCatching { manager.registerService(NsdServiceInfo().apply {
            serviceName = "KIGTTS-${value.id.take(10)}"; serviceType = FaceLanDiscovery.TYPE; port = value.port
            setAttribute("id", value.id); setAttribute("name", value.name); setAttribute("key", value.token)
        }, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { listener.failed() }
    }

    private fun browse() {
        val generation = epoch
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = failed()
            fun failed() { handler.post {
                if (generation == epoch && discovery === this) {
                    discovery = null
                    runCatching { manager.stopServiceDiscovery(this) }
                    handler.removeCallbacks(browseAgain); handler.postDelayed(browseAgain, 2_000)
                }
            } }
            override fun onServiceFound(info: NsdServiceInfo) { handler.post {
                if (generation == epoch && discovery === this && info.serviceName.startsWith("KIGTTS-") &&
                    !info.serviceName.startsWith("KIGTTS-${identity?.id?.take(10)}")) resolver.add(info)
            } }
            override fun onServiceLost(info: NsdServiceInfo) { handler.post {
                if (generation == epoch && discovery === this) {
                    val key = faceNsdKey(info)
                    resolver.remove(key)
                    resolved.remove(key)?.let { id -> if (id !in resolved.values) lost(id) }
                }
            } }
        }
        discovery = listener
        runCatching { manager.discoverServices(FaceLanDiscovery.TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { listener.failed() }
    }

    fun stop() {
        epoch++; identity = null
        handler.removeCallbacks(registerAgain); handler.removeCallbacks(browseAgain)
        resolver.clear()
        registration?.let { runCatching { manager.unregisterService(it) } }; registration = null
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }; discovery = null
        resolved.clear()
    }
}
