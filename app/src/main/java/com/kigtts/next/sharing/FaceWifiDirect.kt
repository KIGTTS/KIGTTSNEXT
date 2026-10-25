package com.kigtts.next.sharing

import android.annotation.SuppressLint
import android.content.*
import android.net.wifi.p2p.*
import android.os.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
internal class FaceWifiDirect(private val context: Context, private val error: (String) -> Unit) {
    private val manager = context.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var waiter: CancellableContinuation<String>? = null
    var address: String? = null; private set
    private var ownsGroup = false
    fun start(receiving: Boolean) {
        if (manager == null) return
        channel?.let { manager.discoverPeers(it, action { }); if (receiving) prepareGroup(it); return }
        val connection = manager.initialize(context, Looper.getMainLooper()) { stop(); error("附近连接已断开，请重新进入面对面") }
        channel = connection
        val events = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                    manager.requestConnectionInfo(connection) { info ->
                        if (info.groupFormed && !info.isGroupOwner) {
                            info.groupOwnerAddress?.hostAddress?.let { host -> waiter?.takeIf { it.isActive }?.resume(host); waiter = null }
                        }
                    }
                    manager.requestGroupInfo(connection) { group -> group?.owner?.deviceAddress?.takeIf { it != "02:00:00:00:00:00" }?.let { address = it } }
                }
            }
        }
        receiver = events
        androidx.core.content.ContextCompat.registerReceiver(context, events, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        manager.discoverPeers(connection, action { })
        if (Build.VERSION.SDK_INT >= 29) manager.requestDeviceInfo(connection) { address = it?.deviceAddress?.takeIf { value -> value != "02:00:00:00:00:00" } }
        if (receiving) prepareGroup(connection)
    }
    private fun prepareGroup(connection: WifiP2pManager.Channel) {
        manager.requestGroupInfo(connection) { existing ->
            if (existing?.isGroupOwner == true) address = existing.owner?.deviceAddress
            else if (existing == null) manager.createGroup(connection, action { success -> ownsGroup = success })
        }
    }
    suspend fun connect(device: String): String = withTimeout(35_000) {
        require(device.matches(Regex("[0-9a-fA-F:]{17}")))
        val active = channel ?: throw IllegalStateException("附近连接尚未就绪")
        suspendCancellableCoroutine { continuation ->
            waiter?.cancel(); waiter = continuation
            continuation.invokeOnCancellation { if (waiter === continuation) waiter = null }
            manager.requestGroupInfo(active) { group ->
                if (!continuation.isActive) return@requestGroupInfo
                if (group?.owner?.deviceAddress.equals(device, true) && group?.isGroupOwner == false) {
                    manager.requestConnectionInfo(active) { info ->
                        if (continuation.isActive) info.groupOwnerAddress?.hostAddress?.let { host -> waiter = null; continuation.resume(host) }
                    }
                } else manager.connect(active, WifiP2pConfig().apply { deviceAddress = device; groupOwnerIntent = 0 }, action { success ->
                    if (!success && continuation.isActive) { waiter = null; continuation.resumeWithException(IllegalStateException("未能连接对方，请确认两边都停留在面对面页面")) }
                })
            }
        }
    }
    fun stopDiscovery() { channel?.let { runCatching { manager.stopPeerDiscovery(it, null) } } }
    fun stop() {
        waiter?.cancel(); waiter = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }; receiver = null
        channel?.let { active -> runCatching { manager.stopPeerDiscovery(active, null); manager.cancelConnect(active, null); if (ownsGroup) manager.removeGroup(active, null); if (Build.VERSION.SDK_INT >= 27) active.close() } }
        channel = null; ownsGroup = false; address = null
    }
    private fun action(done: (Boolean) -> Unit) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = done(true)
        override fun onFailure(reason: Int) = done(false)
    }
}
