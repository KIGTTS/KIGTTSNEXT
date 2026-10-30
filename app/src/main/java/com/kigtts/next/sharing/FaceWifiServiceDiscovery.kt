package com.kigtts.next.sharing

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Handler
import android.util.Log

private const val FACE_P2P_TYPE = "_kigtts-photo._tcp"

/** The remote service callback supplies a connectable MAC even when our own MAC is redacted. */
internal fun faceWifiServicePeer(domain: String, record: Map<String, String>, device: String, self: String): FacePeer? {
    val id = record["id"].orEmpty(); val key = record["key"].orEmpty(); val port = record["port"]?.toIntOrNull() ?: return null
    if (id == self || !id.matches(FACE_LAN_TOKEN) || !key.matches(FACE_LAN_TOKEN) || port !in 1..65535 ||
        !device.matches(Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) || device == "02:00:00:00:00:00" ||
        !domain.trimEnd('.').equals("KIGTTS-${id.take(10)}.$FACE_P2P_TYPE.local", true)) return null
    return FacePeer(id, record["name"].orEmpty().take(40), port = port, token = key, p2p = device)
}

/** Own only this session's registration/request. Never clear other apps' P2P services. */
@SuppressLint("MissingPermission")
internal class FaceWifiServiceDiscovery(private val manager: WifiP2pManager, private val channel: WifiP2pManager.Channel,
    private val handler: Handler, private val found: (FacePeer) -> Unit) {
    private var identity: FaceLanIdentity? = null
    private var service: WifiP2pDnsSdServiceInfo? = null
    private var request: WifiP2pDnsSdServiceRequest? = null
    private var generation = 0
    private var paused = false
    private val scan = Runnable { discover() }
    private val register = Runnable { identity?.let(::advertise) }
    private val queryAgain = Runnable { query() }
    fun start(value: FaceLanIdentity) {
        if (identity == value) return
        stop(); identity = value
        val epoch = generation
        manager.setDnsSdResponseListeners(channel, { _, _, _ -> }, { domain, record, device ->
            if (generation == epoch) faceWifiServicePeer(domain, record, device.deviceAddress, value.id)?.let(found)
        })
        advertise(value)
        query()
    }
    private fun query() {
        if (identity == null) return
        val epoch = generation
        // A type-only request asks for PTR records, not the TXT record carrying our session.
        // Request all DNS-SD records, then accept only our exact domain/token in the callback.
        val query = WifiP2pDnsSdServiceRequest.newInstance()
        request = query
        manager.addServiceRequest(channel, query, action { success ->
            if (generation == epoch) {
                if (success) discover()
                else {
                    runCatching { manager.removeServiceRequest(channel, query, null) }; request = null
                    handler.removeCallbacks(queryAgain); handler.postDelayed(queryAgain, 2_000)
                }
            }
        })
    }
    private fun advertise(value: FaceLanIdentity) {
        val epoch = generation
        val local = WifiP2pDnsSdServiceInfo.newInstance("KIGTTS-${value.id.take(10)}", FACE_P2P_TYPE,
            mapOf("id" to value.id, "key" to value.token, "port" to value.port.toString(), "name" to value.name.take(40)))
        service = local
        manager.addLocalService(channel, local, action { success ->
            if (!success && generation == epoch) {
                service = null; handler.removeCallbacks(register); handler.postDelayed(register, 2_000)
            }
        })
    }
    private fun discover() {
        if (identity == null || paused) return
        val epoch = generation
        manager.discoverServices(channel, action { success ->
            if (generation == epoch && !paused) {
                if (!success) Log.d("FaceTransfer", "Wi-Fi Direct service discovery will retry")
                handler.removeCallbacks(scan); handler.postDelayed(scan, if (success) 12_000 else 2_000)
            }
        })
    }
    fun pause() { paused = true; handler.removeCallbacks(scan) }
    fun resume() { paused = false; if (identity != null) { handler.removeCallbacks(scan); handler.postDelayed(scan, 12_000) } }
    fun stop() {
        generation++; identity = null; paused = false
        handler.removeCallbacks(scan); handler.removeCallbacks(register); handler.removeCallbacks(queryAgain)
        service?.let { runCatching { manager.removeLocalService(channel, it, null) } }; service = null
        request?.let { runCatching { manager.removeServiceRequest(channel, it, null) } }; request = null
        runCatching { manager.setDnsSdResponseListeners(channel, null, null) }
    }
    private fun action(done: (Boolean) -> Unit) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = done(true)
        override fun onFailure(reason: Int) = done(false)
    }
}
