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
    private var waitingDevice: String? = null
    var address: String? = null; private set
    private var ownsGroup = false
    fun start(receiving: Boolean) {
        if (manager == null) return
        channel?.let { manager.discoverPeers(it, action { }); if (receiving) prepareGroup(it); return }
        val connection = manager.initialize(context, Looper.getMainLooper()) { stop(); error("附近连接已断开，请重新查找") }
        channel = connection
        val events = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                    if (Build.VERSION.SDK_INT >= 29) manager.requestDeviceInfo(connection) {
                        it?.deviceAddress?.takeIf { value -> value != "02:00:00:00:00:00" }?.let { value -> address = value }
                    }
                    manager.requestGroupInfo(connection) { group ->
                        if (group?.isGroupOwner == true) group.owner?.deviceAddress?.takeIf { it != "02:00:00:00:00:00" }?.let { address = it }
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
        manager.discoverPeers(connection, action { })
        if (Build.VERSION.SDK_INT >= 29) manager.requestDeviceInfo(connection) { address = it?.deviceAddress?.takeIf { value -> value != "02:00:00:00:00:00" } }
        if (receiving) prepareGroup(connection)
    }
    private fun prepareGroup(connection: WifiP2pManager.Channel) {
        if (waiter?.isActive == true) return
        manager.requestGroupInfo(connection) { existing ->
            if (existing?.isGroupOwner == true) address = existing.owner?.deviceAddress
            else if (existing == null) manager.createGroup(connection, action { success -> ownsGroup = success })
        }
    }
    suspend fun connect(device: String): String = withTimeout(35_000) {
        require(device.matches(Regex("[0-9a-fA-F:]{17}")))
        val active = channel ?: throw IllegalStateException("附近连接尚未就绪")
        val group = currentGroup(active)
        if (group?.isGroupOwner == true && ownsGroup) {
            // Both users may have receiving enabled. Leave only the group created by this session before joining the recipient.
            suspendCancellableCoroutine<Unit> { continuation ->
                manager.removeGroup(active, action { success -> if (continuation.isActive) {
                    if (success) { ownsGroup = false; continuation.resume(Unit) }
                    else continuation.resumeWithException(IllegalStateException("附近连接忙碌，请稍后重试或使用同一 Wi-Fi"))
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
                } else manager.connect(active, WifiP2pConfig().apply { deviceAddress = device; groupOwnerIntent = 0 }, action { success ->
                    if (!success && continuation.isActive) { waiter = null; waitingDevice = null
                        continuation.resumeWithException(IllegalStateException("无法建立附近连接，请确认对方已开启面对面接收；也可以连接同一 Wi-Fi 或使用扫码领取")) }
                })
            }
        }
    }
    private suspend fun currentGroup(active: WifiP2pManager.Channel): WifiP2pGroup? = suspendCancellableCoroutine { continuation ->
        manager.requestGroupInfo(active) { if (continuation.isActive) continuation.resume(it) }
    }
    fun stopDiscovery() { channel?.let { runCatching { manager.stopPeerDiscovery(it, null) } } }
    fun stop() {
        waiter?.cancel(); waiter = null; waitingDevice = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }; receiver = null
        channel?.let { active -> runCatching { manager.stopPeerDiscovery(active, null); manager.cancelConnect(active, null); if (ownsGroup) manager.removeGroup(active, null); if (Build.VERSION.SDK_INT >= 27) active.close() } }
        channel = null; ownsGroup = false; address = null
    }
    private fun action(done: (Boolean) -> Unit) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = done(true)
        override fun onFailure(reason: Int) = done(false)
    }
}
