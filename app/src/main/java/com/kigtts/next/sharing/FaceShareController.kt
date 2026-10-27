package com.kigtts.next.sharing

import android.content.Context
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.identity.IdentityRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

internal class FaceShareController(private val context: Context, private val scope: CoroutineScope,
    private val cards: CardRepository, private val identities: IdentityRepository, private val message: (String) -> Unit) {
    private val mutablePanel = MutableStateFlow<FacePanel?>(null); val panel = mutablePanel.asStateFlow()
    private val mutablePeers = MutableStateFlow<List<FacePeer>>(emptyList()); val peers = mutablePeers.asStateFlow()
    private val mutableRequests = MutableStateFlow<List<FaceOffer>>(emptyList()); val requests = mutableRequests.asStateFlow()
    private val mutableUrl = MutableStateFlow<String?>(null); val url = mutableUrl.asStateFlow()
    private val mutableBusy = MutableStateFlow(false); val busy = mutableBusy.asStateFlow()
    private val mutableStatus = MutableStateFlow(""); val status = mutableStatus.asStateFlow()
    private val mutableProgress = MutableStateFlow(0f); val progress = mutableProgress.asStateFlow()
    val hotspot = FaceHotspot(context) { message(it) }
    private val media = FacePhotoRepository(cards, identities)
    private val webIcons = FaceWebIcons(context)
    private val profileLookups = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var session: FacePhotoSession? = null
    private var collector: Job? = null; private var transfer: Job? = null; private var addressing: Job? = null
    private var files = emptyList<FaceAsset>()
    private val clientSession = faceToken()
    private val sessionId = faceToken()
    private val wifi = FaceWifiDirect(context, message)
    private val lan = FaceLanDiscovery(context, ::found) { id -> mutablePeers.update { it.filterNot { peer -> peer.id == id } } }
    private val ble = FaceBluetoothDiscovery(context, {
        JSONObject().put("id", sessionId).put("nickname", profile().optString("nickname").take(30)).put("port", session?.server?.port ?: 0)
            .put("token", session?.token ?: "").put("p2p", wifi.address ?: "")
    }, ::found)
    private var connected: FacePeer? = null
    private var avatar: String? = null
    private var generation = 0
    private val sessionLock = Mutex()
    private fun profile() = (identities.current?.publicJson() ?: JSONObject().put("nickname", "KIGTTS 用户")).put("id", sessionId).put("avatar", avatar ?: "")
    fun openSend(ids: List<String>) { generation++; mutablePanel.value = FacePanel(ids.distinct(), personal = ids.distinct().singleOrNull() == identities.current?.designId) }
    fun openReceive() { generation++; mutablePanel.value = FacePanel(emptyList(), FaceMode.RECEIVE) }
    fun hide() { generation++; mutablePanel.value = null; stopDiscovery() }
    private fun found(peer: FacePeer) {
        if (peer.id == sessionId) return
        mutablePeers.update { before ->
            val old = before.firstOrNull { it.id == peer.id }
            val combined = peer.copy(host = peer.host ?: old?.host, p2p = peer.p2p ?: old?.p2p,
                bluetooth = peer.bluetooth ?: old?.bluetooth, signal = maxOf(peer.signal, old?.signal ?: -100))
            (before.filterNot { it.id == peer.id } + combined.copy(avatar = peer.avatar ?: old?.avatar, profile = peer.profile ?: old?.profile)).sortedByDescending { it.signal }.take(64)
        }
        val epoch = generation
        if (peer.host != null && peers.value.firstOrNull { it.id == peer.id }?.profile == null && profileLookups.add(peer.id + peer.token)) scope.launch(Dispatchers.IO) {
            runCatching { FaceTransferClient.request(peer.host, peer.port, "GET", "/s/${peer.token}/hello", owner = clientSession, timeoutMillis = 7_000) }.getOrNull()?.let { info ->
                val image = info.optString("avatar").takeIf { it.isNotBlank() && it.length < 65536 }
                if (epoch == generation) mutablePeers.update { list -> list.map { if (it.id == peer.id) it.copy(avatar = image, profile = info.toString()) else it } }
            }
            if (epoch != generation) profileLookups.remove(peer.id + peer.token)
        }
    }
    private suspend fun ensureSession(): FacePhotoSession = sessionLock.withLock {
        session?.let { return it }
        val epoch = generation
        withContext(NonCancellable) {
        val created = withContext(Dispatchers.IO) {
            avatar = identities.current?.avatar?.let(cards.stickers::imageFile)?.let(::faceAvatarData)
            FacePhotoSession(File(context.cacheDir, "face-share/${faceToken()}"), ::profile, webIcons::icon) { offer, assets ->
                media.receive(offer, assets)
                val pictures = assets.filterNot { it.format == "PROFILE" }
                mutableStatus.value = if (pictures.all { it.format == "BANNER" }) "名片已加入扩列收藏夹" else "已接收 ${pictures.map { it.photo }.distinct().size} 张照片"
            }
        }
        if (epoch != generation || !scope.isActive) {
            withContext(Dispatchers.IO) { created.close() }
            throw CancellationException("Sharing was closed")
        }
            session = created
            collector = scope.launch { created.requests.collect { mutableRequests.value = it } }
            FaceShareService.start(context, ::stop)
            addressing = scope.launch { while (isActive) {
                val addresses = withContext(Dispatchers.IO) { localWifiAddresses(context) }
                val address = if (hotspot.details.value != null) addresses.firstOrNull { it.endsWith(".1") } ?: addresses.firstOrNull() else addresses.firstOrNull()
                mutableUrl.value = address?.let { "http://$it:${created.server.port}${created.path}" }
                delay(1500)
            } }
            created
        }
    }
    fun prepare(mode: FaceMode, original: Boolean, composed: Boolean) {
        val current = panel.value ?: return
        val epoch = generation
        if (mutableBusy.value) return
        mutableBusy.value = true; mutableStatus.value = if (current.personal) "正在准备名片" else "正在准备照片"
        transfer = scope.launch {
            try {
                val active = ensureSession()
                val prepared = withContext(Dispatchers.IO) { media.prepare(current.ids, original, composed, active.root) }
                if (epoch != generation) return@launch
                files = prepared; active.assets.putAll(prepared.associateBy { it.id })
                mutablePanel.value = current.copy(mode = mode); mutableStatus.value = ""
                if (mode == FaceMode.SEND) discover()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "照片未能准备好，请重试") }
            finally { mutableBusy.value = false }
        }
    }
    fun receive() {
        if (mutableBusy.value || panel.value?.mode != FaceMode.RECEIVE) return
        val epoch = generation
        transfer = scope.launch {
            try { ensureSession(); if (epoch == generation && panel.value?.mode == FaceMode.RECEIVE) discover() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("接收未能开启，请重试") }
        }
    }
    fun discover() {
        if (panel.value?.mode !in setOf(FaceMode.SEND, FaceMode.RECEIVE)) return
        val active = session ?: return
        val receiving = panel.value?.mode == FaceMode.RECEIVE
        lan.start(sessionId, profile().optString("nickname"), active.server.port, active.token, receiving)
        runCatching { wifi.start(receiving) }
        runCatching { ble.start(receiving) }
    }
    fun stopDiscovery() { lan.stop(); ble.stop(); wifi.stopDiscovery() }
    fun send(peer: FacePeer) {
        if (mutableBusy.value || files.isEmpty()) return
        mutableBusy.value = true; mutableProgress.value = 0f; mutableStatus.value = "正在连接 ${peer.name}"
        transfer = scope.launch {
            try {
                val resolved = connected?.takeIf { it.id == peer.id } ?: if (peer.host != null) peer else {
                    val address = wifi.connect(requireNotNull(peer.p2p) { "对方尚未准备好，请稍后重试或改用扫码领取" })
                    peer.copy(host = address)
                }
                connected = resolved
                found(resolved)
                mutableStatus.value = "等待对方接收"
                FaceTransferClient.send(resolved, clientSession, profile(), files) { value ->
                    mutableProgress.value = value; mutableStatus.value = "正在发送 ${(value * 100).toInt()}%"
                }
                val sent = if (panel.value?.personal == true) "名片已送达" else "照片已送达"
                mutableStatus.value = sent; message(sent)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutableStatus.value = "发送未完成"; message(error.message ?: "未能连接对方，请重试") }
            finally { mutableBusy.value = false }
        }
    }
    fun startHotspot() { hotspot.start() }
    fun approve(id: String, auto: Boolean) { val active = session; scope.launch(Dispatchers.IO) { active?.approve(id, auto) } }
    fun reject(id: String) { val active = session; scope.launch(Dispatchers.IO) { active?.reject(id) } }
    fun stop() {
        generation++
        transfer?.cancel(); FaceTransferClient.cancel(clientSession); collector?.cancel(); addressing?.cancel(); stopDiscovery(); wifi.stop(); hotspot.stop()
        val old = session; session = null; files = emptyList(); connected = null
        old?.revoke()
        mutablePeers.value = emptyList(); mutableRequests.value = emptyList(); mutableUrl.value = null
        profileLookups.clear()
        mutablePanel.value = null; mutableBusy.value = false; mutableStatus.value = ""; FaceShareService.finish(context)
        if (old != null) CoroutineScope(Dispatchers.IO).launch { old.close() }
    }
    fun dispose() { stop() }
}
