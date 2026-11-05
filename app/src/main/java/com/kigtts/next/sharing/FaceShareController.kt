package com.kigtts.next.sharing

import android.content.Context
import android.util.Log
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.identity.IdentityRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

private const val SHARE_LOG_TAG = "FaceTransfer"

internal class FaceShareController(private val context: Context, private val scope: CoroutineScope,
    private val cards: CardRepository, private val identities: IdentityRepository, private val message: (String) -> Unit,
    private val outgoing: ((List<String>) -> Unit)? = null) {
    private val mutablePanel = MutableStateFlow<FacePanel?>(null); val panel = mutablePanel.asStateFlow()
    private val mutablePeers = MutableStateFlow<List<FacePeer>>(emptyList()); val peers = mutablePeers.asStateFlow()
    private val mutableRequests = MutableStateFlow<List<FaceOffer>>(emptyList()); val requests = mutableRequests.asStateFlow()
    private val mutableUrl = MutableStateFlow<String?>(null); val url = mutableUrl.asStateFlow()
    private val mutableBusy = MutableStateFlow(false); val busy = mutableBusy.asStateFlow()
    private val mutableStatus = MutableStateFlow(""); val status = mutableStatus.asStateFlow()
    private val mutableProgress = MutableStateFlow(0f); val progress = mutableProgress.asStateFlow()
    private val preferences = context.getSharedPreferences("face-receiving", Context.MODE_PRIVATE)
    private val mutableReceiving = MutableStateFlow(preferences.getBoolean("enabled", false)); val receiving = mutableReceiving.asStateFlow()
    private val incomingNotifications = FaceIncomingNotifications(context)
    val hotspot = FaceHotspot(context) { message(it) }
    private val media = FacePhotoRepository(cards, identities)
    private val webIcons = FaceWebIcons(context)
    private var session: FacePhotoSession? = null
    private var collector: Job? = null; private var transfer: Job? = null; private var addressing: Job? = null
    private var starting: Job? = null
    private val clientSession = faceToken()
    private val sessionId = faceToken()
    private val profiles = FacePeerResolver(scope, clientSession, { peer ->
        mutablePeers.update { before -> before.map { if (it.id == peer.id && it.token == peer.token && it.port == peer.port)
            mergeFacePeer(it, peer) else it } }
    })
    @Volatile private var advertisedHosts = emptyList<String>()
    private val wifi = FaceWifiDirect(context, ::found, message)
    private val lan = FaceLanDiscovery(context, ::found) { id -> mutablePeers.update { it.filterNot { peer -> peer.id == id } } }
    private val ble = FaceBluetoothDiscovery(context, {
        faceBluetoothDescriptor(sessionId, profile(), session?.server?.port ?: 0, session?.peerToken ?: "", wifi.address, advertisedHosts)
    }, ::found, { bluetoothAvatar })
    @Volatile private var avatar: String? = null
    @Volatile private var bluetoothAvatar: ByteArray? = null
    private var avatarAsset: String? = null
    private var generation = 0
    private val sessionLock = Mutex()
    private fun profile() = (identities.current?.publicJson() ?: JSONObject().put("nickname", "KIGTTS 用户")).put("id", sessionId).put("avatar", avatar ?: "")
    fun openSend(ids: List<String>) {
        outgoing?.let { it(ids.distinct()); return }
        generation++; starting?.cancel(); starting = null
        val selected = ids.distinct()
        mutablePanel.value = FacePanel(selected, FaceMode.SEND, personal = identities.current?.designId?.let { selected.singleOrNull() == it } == true)
    }
    fun openReceive() { setReceiving(true); message("面对面接收已开启") }
    fun setReceiving(enabled: Boolean) {
        mutableReceiving.value = enabled; preferences.edit().putBoolean("enabled", enabled).apply()
        if (enabled) startDiscovery()
        else if (panel.value == null && !busy.value) release()
        else if (panel.value?.mode != FaceMode.SEND) stopDiscovery()
    }
    fun foreground(visible: Boolean) {
        incomingNotifications.foreground = visible
        if (visible) startDiscovery() else if (!receiving.value) stopDiscovery()
    }
    fun hide() {
        if (!receiving.value && !mutableBusy.value && session?.hasActiveOffers != true && session?.assets?.isNotEmpty() != true) { stop(); return }
        generation++; starting?.cancel(); starting = null; mutablePanel.value = null
        if (receiving.value) startDiscovery() else stopDiscovery()
    }
    private fun found(peer: FacePeer) {
        if (peer.id == sessionId) return
        if (Log.isLoggable(SHARE_LOG_TAG, Log.DEBUG))
            Log.d(SHARE_LOG_TAG, "Discovered addresses=${facePeerHosts(peer)} port=${peer.port} p2p=${peer.p2p != null} profile=${peer.profile != null}")
        mutablePeers.update { before ->
            val old = before.firstOrNull { it.id == peer.id }
            (before.filterNot { it.id == peer.id } + mergeFacePeer(old, peer)).sortedByDescending { it.signal }.take(64)
        }
        peers.value.firstOrNull { it.id == peer.id }?.let(profiles::refresh)
    }
    private suspend fun ensureSession(): FacePhotoSession = sessionLock.withLock {
        session?.let { return it }
        val epoch = generation
        withContext(NonCancellable) {
        val created = withContext(Dispatchers.IO) {
            avatarAsset = identities.current?.avatar
            avatar = avatarAsset?.let(cards.stickers::imageFile)?.let(::faceAvatarData)
            bluetoothAvatar = faceBluetoothAvatar(avatar)
            if (Log.isLoggable(SHARE_LOG_TAG, Log.DEBUG))
                Log.d(SHARE_LOG_TAG, "Identity avatar configured=${avatarAsset != null} encoded=${avatar != null}")
            FacePhotoSession(File(context.cacheDir, "face-share/${faceToken()}"), ::profile, webIcons::icon) { offer, assets ->
                media.receive(offer, assets)
                val pictures = assets.filterNot { it.format == "PROFILE" }
                val summary = if (pictures.all { it.format == "BANNER" }) "名片已加入扩列收藏夹" else "已接收 ${pictures.map { it.photo }.distinct().size} 张照片"
                scope.launch { message(summary) }
            }
        }
        if (epoch != generation || !scope.isActive) {
            withContext(Dispatchers.IO) { created.close() }
            throw CancellationException("Sharing was closed")
        }
            session = created
            collector = scope.launch { created.requests.collect { mutableRequests.value = it; incomingNotifications.update(it) } }
            FaceShareService.start(context, ::stop) { id, accept -> if (accept) approve(id, false) else reject(id) }
            addressing = scope.launch { while (isActive) {
                val addresses = withContext(Dispatchers.IO) { localWifiAddresses(context) }
                advertisedHosts = addresses.filter(::facePrivateHost).take(4)
                val currentAvatar = identities.current?.avatar
                if (currentAvatar != avatarAsset) {
                    avatar = withContext(Dispatchers.IO) { currentAvatar?.let(cards.stickers::imageFile)?.let(::faceAvatarData) }
                    bluetoothAvatar = withContext(Dispatchers.IO) { faceBluetoothAvatar(avatar) }
                    avatarAsset = currentAvatar
                }
                val address = if (hotspot.details.value != null) addresses.firstOrNull { it.endsWith(".1") } ?: addresses.firstOrNull() else addresses.firstOrNull()
                mutableUrl.value = address?.let { "http://$it:${created.server.port}${created.path}" }
                delay(1500)
            } }
            created
        }
    }
    fun prepareBrowser(original: Boolean, composed: Boolean) {
        val current = panel.value ?: return
        val epoch = generation
        if (mutableBusy.value) return
        mutableBusy.value = true; mutableStatus.value = if (current.personal) "正在准备名片" else "正在准备照片"
        transfer = scope.launch {
            try {
                val active = ensureSession()
                val prepared = withContext(Dispatchers.IO) { media.prepare(current.ids, original, composed, active.root) }
                if (epoch != generation) return@launch
                active.assets.clear(); active.assets.putAll(prepared.associateBy { it.id })
                mutablePanel.value = current.copy(mode = FaceMode.BROWSER); mutableStatus.value = ""
                if (!receiving.value) stopDiscovery()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "照片未能准备好，请重试") }
            finally { mutableBusy.value = false }
        }
    }
    fun startDiscovery() {
        if ((!receiving.value && panel.value?.mode != FaceMode.SEND) || starting?.isActive == true) return
        val epoch = generation
        starting = scope.launch {
            try { ensureSession(); if (epoch == generation) discover() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (receiving.value) setReceiving(false); message("面对面未能开启，请重试") }
        }
    }
    fun returnToShare() { mutablePanel.value = panel.value?.copy(mode = FaceMode.SEND); startDiscovery() }
    fun discover() {
        if (!receiving.value && panel.value?.mode != FaceMode.SEND) return
        val active = session ?: return
        lan.start(sessionId, profile().optString("nickname"), active.server.port, active.peerToken)
        runCatching { wifi.start(FaceLanIdentity(sessionId, profile().optString("nickname"), active.server.port, active.peerToken)) }
        runCatching { ble.start(true) }
    }
    fun refreshDiscovery() { if (!mutableBusy.value) { stopDiscovery(); startDiscovery() } }
    fun stopDiscovery() { lan.stop(); ble.stop(); wifi.stopDiscovery(); profiles.clear() }
    fun send(peer: FacePeer, original: Boolean = false, composed: Boolean = true) {
        val current = panel.value?.takeIf { it.mode == FaceMode.SEND } ?: return
        if (mutableBusy.value) return
        val epoch = generation
        mutableBusy.value = true; mutableProgress.value = 0f; mutableStatus.value = if (current.personal) "正在准备名片" else "正在准备照片"
        transfer = scope.launch {
            try {
                val active = ensureSession()
                val files = withContext(Dispatchers.IO) { media.prepare(current.ids, original, composed, active.root) }
                if (epoch != generation) return@launch
                mutableStatus.value = "正在连接 ${peer.name}"
                val target = peers.value.firstOrNull { it.id == peer.id && it.token == peer.token } ?: peer
                val resolved = profiles.resolve(target) ?: run {
                    val device = target.p2p ?: withTimeoutOrNull(5_000) {
                        peers.first { list -> list.any { it.id == target.id && it.token == target.token && it.p2p != null } }
                            .first { it.id == target.id && it.token == target.token }.p2p
                    } ?: error("无法连接对方，请确认 Wi-Fi 允许设备互访，或使用扫码领取")
                    mutableStatus.value = "正在建立附近直连，请留意系统连接提示"
                    val address = wifi.connect(device)
                    (if (address != null) profiles.resolve(target.copy(host = address, hosts = emptyList()))
                    else withTimeoutOrNull(12_000) {
                        var connected: FacePeer? = null
                        while (connected == null) {
                            val updated = peers.value.firstOrNull { it.id == target.id && it.token == target.token } ?: target
                            connected = profiles.resolve(updated)
                            if (connected == null) delay(500)
                        }
                        connected
                    })
                        ?: error("对方暂时无法接收，请重新查找或使用扫码领取")
                }
                found(resolved)
                mutableStatus.value = "等待对方接收"
                FaceTransferClient.send(resolved, clientSession, profile(), files) { value ->
                    mutableProgress.value = value; mutableStatus.value = "正在发送 ${(value * 100).toInt()}%"
                }
                val sent = if (panel.value?.personal == true) "名片已送达" else "照片已送达"
                mutableStatus.value = sent; message(sent)
            } catch (_: TimeoutCancellationException) {
                mutableStatus.value = "发送未完成"; message("连接或接收超时，请重新查找对方后再试")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                Log.w(SHARE_LOG_TAG, "Send failed (${error.javaClass.simpleName})", error)
                mutableStatus.value = "发送未完成"; message(error.message ?: "未能连接对方，请重试")
            }
            finally { mutableBusy.value = false }
        }
    }
    fun shareSystem(original: Boolean, composed: Boolean) {
        val current = panel.value ?: return
        if (mutableBusy.value) return
        val epoch = generation
        mutableBusy.value = true; mutableStatus.value = if (current.personal) "正在准备名片" else "正在准备照片"
        transfer = scope.launch {
            try {
                val active = ensureSession()
                val intent = withContext(Dispatchers.IO) {
                    val assets = media.prepare(current.ids, original, composed, active.root, includeMotion = true)
                    faceSystemShareIntent(context, assets, current.personal)
                }
                if (epoch == generation) { context.startActivity(intent); mutableStatus.value = "" }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "未能打开系统分享"); mutableStatus.value = "" }
            finally { mutableBusy.value = false }
        }
    }
    fun connectionMessage(value: String) { message(value) }
    fun startHotspot() {
        if (panel.value == null) return
        val epoch = generation
        scope.launch {
            if (epoch != generation || panel.value == null) return@launch
            try {
                ensureSession()
                if (epoch == generation && panel.value != null) hotspot.start()
            }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("面对面未能开启，请重试") }
        }
    }
    fun approve(id: String, auto: Boolean) { val active = session; scope.launch(Dispatchers.IO) { active?.approve(id, auto) } }
    fun reject(id: String) { val active = session; scope.launch(Dispatchers.IO) { active?.reject(id) } }
    fun finishSending() {
        generation++; transfer?.cancel(); starting?.cancel(); starting = null; FaceTransferClient.cancel(clientSession)
        session?.endBrowserShare(); if (!receiving.value) hotspot.stop()
        mutablePanel.value = null; mutableBusy.value = false; mutableStatus.value = ""; mutableProgress.value = 0f; mutableUrl.value = null
        if (receiving.value) startDiscovery()
        else if (session?.hasActiveOffers == true) stopDiscovery()
        else release()
    }
    fun stop() {
        mutableReceiving.value = false; preferences.edit().putBoolean("enabled", false).apply()
        release()
    }
    private fun release() {
        generation++
        transfer?.cancel(); starting?.cancel(); FaceTransferClient.cancel(clientSession); collector?.cancel(); addressing?.cancel(); stopDiscovery(); wifi.stop(); hotspot.stop()
        val old = session; session = null
        old?.revoke()
        mutablePeers.value = emptyList(); mutableRequests.value = emptyList(); mutableUrl.value = null
        profiles.clear(); advertisedHosts = emptyList()
        incomingNotifications.clear()
        mutablePanel.value = null; mutableBusy.value = false; mutableStatus.value = ""; FaceShareService.finish(context)
        if (old != null) CoroutineScope(Dispatchers.IO).launch { old.close() }
    }
    fun dispose() { release() }
}
