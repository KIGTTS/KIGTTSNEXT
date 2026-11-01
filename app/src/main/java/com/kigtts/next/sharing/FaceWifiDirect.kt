package com.kigtts.next.sharing

import android.annotation.SuppressLint
import android.content.*
import android.net.wifi.p2p.*
import android.os.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
internal class FaceWifiDirect(private val context: Context, private val found: (FacePeer) -> Unit = {}, private val error: (String) -> Unit) {
    private val manager = context.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var waiter: CancellableContinuation<String?>? = null
    private var waitingDevice: String? = null
    var address: String? = null; private set
    private var services: FaceWifiServiceDiscovery? = null
    fun start(identity: FaceLanIdentity? = null) {
        if (manager == null) return
        channel?.let { identity?.let { value -> services?.start(value) }; return }
        val connection = manager.initialize(context, Looper.getMainLooper()) { stop(); error("附近连接已断开，请重新查找") }
        channel = connection
        services = FaceWifiServiceDiscovery(manager, connection, Handler(Looper.getMainLooper()), found)
        val events = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                    refreshAddress(connection)
                    manager.requestGroupInfo(connection) { group ->
                        if (group?.isGroupOwner == true) {
                            group.owner?.deviceAddress?.takeIf { it != "02:00:00:00:00:00" }?.let { address = it }
                            if (waitingDevice != null && group.clientList.any { it.deviceAddress.equals(waitingDevice, true) }) {
                                waiter?.takeIf { it.isActive }?.resume(null); waiter = null; waitingDevice = null
                            }
                        }
                        else if (waitingDevice != null && group?.owner?.deviceAddress.equals(waitingDevice, true))
                            manager.requestConnectionInfo(connection) { info ->
                                if (info.groupFormed && !info.isGroupOwner) info.groupOwnerAddress?.hostAddress?.let { host ->
                                    waiter?.takeIf { it.isActive }?.resume(host); waiter = null; waitingDevice = null
                                }
                            }
                    }
                }
            }
        }
        receiver = events
        androidx.core.content.ContextCompat.registerReceiver(context, events, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        runCatching { manager.discoverPeers(connection, action { }) }
        refreshAddress(connection)
        identity?.let { services?.start(it) }
        // Do not create autonomous groups while merely advertising/receiving. On some radios this
        // disrupts infrastructure Wi-Fi, and two existing group owners cannot negotiate normally.
    }
    private fun refreshAddress(connection: WifiP2pManager.Channel) {
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            manager?.requestDeviceInfo(connection) { address = it?.deviceAddress?.takeIf { value -> value != "02:00:00:00:00:00" } }
        }
    }
    /** Null means this phone owns the formed group; discover the selected client's address next. */
    suspend fun connect(device: String): String? = try {
        services?.pause()
        withTimeout(35_000) {
            require(device.matches(Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")))
            val active = channel ?: throw IllegalStateException("附近连接尚未就绪")
            val existing = currentGroup(active)
            if (existing?.isGroupOwner == true && existing.clientList.isEmpty()) {
                // Some OEMs retain an empty autonomous group after its process dies. Only release
                // an idle group when the user starts a transfer; preserve groups with active peers.
                suspendCancellableCoroutine<Unit> { continuation ->
                    manager.removeGroup(active, action { success -> if (continuation.isActive) {
                        if (success) continuation.resume(Unit)
                        else continuation.resumeWithException(IllegalStateException("附近连接忙碌，请稍后重试"))
                    } })
                }
                withTimeout(5_000) { while (currentGroup(active) != null) delay(150) }
            }
            suspendCancellableCoroutine { continuation ->
                waiter?.cancel(); waiter = continuation; waitingDevice = device
                continuation.invokeOnCancellation { if (waiter === continuation) {
                    waiter = null; waitingDevice = null; runCatching { manager.cancelConnect(active, null) }
                } }
                manager.requestGroupInfo(active) { group ->
                    if (!continuation.isActive) return@requestGroupInfo
                    if (group?.owner?.deviceAddress.equals(device, true) && group?.isGroupOwner == false) {
                        manager.requestConnectionInfo(active) { info ->
                            if (continuation.isActive) info.groupOwnerAddress?.hostAddress?.let { host -> waiter = null; waitingDevice = null; continuation.resume(host) }
                        }
                    } else if (group?.isGroupOwner == true && group.clientList.any { it.deviceAddress.equals(device, true) }) {
                        waiter = null; waitingDevice = null; continuation.resume(null)
                    } else manager.connect(active, WifiP2pConfig().apply { deviceAddress = device; groupOwnerIntent = 0 }, action { success ->
                        if (!success && continuation.isActive) { waiter = null; waitingDevice = null
                            continuation.resumeWithException(IllegalStateException("无法建立附近连接，请确认对方已开启面对面接收；也可以连接同一 Wi-Fi 或使用扫码领取")) }
                    })
                }
            }
        }
    } finally { services?.resume() }
    private suspend fun currentGroup(active: WifiP2pManager.Channel): WifiP2pGroup? = suspendCancellableCoroutine { continuation ->
        manager?.requestGroupInfo(active) { if (continuation.isActive) continuation.resume(it) }
    }
    fun stopDiscovery() { services?.stop(); channel?.let { runCatching { manager.stopPeerDiscovery(it, null) } } }
    fun stop() {
        waiter?.cancel(); waiter = null; waitingDevice = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }; receiver = null
        services?.stop(); services = null
        channel?.let { active -> runCatching { manager.stopPeerDiscovery(active, null); manager.cancelConnect(active, null); if (Build.VERSION.SDK_INT >= 27) active.close() } }
        channel = null; address = null
    }
    private fun action(done: (Boolean) -> Unit) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = done(true)
        override fun onFailure(reason: Int) = done(false)
    }
}
