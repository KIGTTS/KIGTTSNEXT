package com.kigtts.next.sharing

import android.content.Context
import android.net.nsd.*

@Suppress("DEPRECATION")
internal class FaceLanDiscovery(context: Context, private val found: (FacePeer) -> Unit, private val lost: (String) -> Unit) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var epoch = 0
    private val resolved = mutableMapOf<String, String>()
    fun start(id: String, name: String, port: Int, token: String, receiving: Boolean) {
        stop(); val generation = epoch
        if (receiving) {
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) { }
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { }
                override fun onServiceUnregistered(info: NsdServiceInfo) { }
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) { }
            }
            registration = listener
            runCatching { manager.registerService(NsdServiceInfo().apply {
                serviceName = "KIGTTS-${id.take(10)}"; serviceType = TYPE; setPort(port)
                setAttribute("id", id); setAttribute("name", name.take(40)); setAttribute("key", token)
            }, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { }
            override fun onDiscoveryStopped(type: String) { }
            override fun onStartDiscoveryFailed(type: String, code: Int) { }
            override fun onStopDiscoveryFailed(type: String, code: Int) { }
            override fun onServiceLost(info: NsdServiceInfo) { synchronized(resolved) { resolved.remove(info.serviceName) }?.let(lost) }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceName.startsWith("KIGTTS-")) return
                runCatching { manager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) { }
                    override fun onServiceResolved(value: NsdServiceInfo) {
                        if (generation != epoch) return
                        fun attr(key: String) = value.attributes[key]?.toString(Charsets.UTF_8).orEmpty()
                        val peerId = attr("id"); val key = attr("key")
                        if (peerId == id || peerId.isBlank() || !key.matches(Regex("[A-Za-z0-9_-]{32,64}"))) return
                        val address = value.host?.takeIf { it.isSiteLocalAddress || it.isLinkLocalAddress }?.hostAddress ?: return
                        synchronized(resolved) { resolved[value.serviceName] = peerId }
                        found(FacePeer(peerId, attr("name").take(40), address, value.port, key))
                    }
                }) }
            }
        }
        discovery = listener
        runCatching { manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }
    fun stop() {
        epoch++; registration?.let { runCatching { manager.unregisterService(it) } }; registration = null
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }; discovery = null
        synchronized(resolved) { resolved.clear() }
    }
    companion object { const val TYPE = "_kigtts-photo._tcp." }
}
