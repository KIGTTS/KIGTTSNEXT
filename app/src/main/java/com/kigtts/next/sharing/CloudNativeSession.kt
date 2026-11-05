package com.kigtts.next.sharing

import androidx.compose.runtime.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.PageBlock
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.*

internal data class CloudNativePanel(val owner: String, val pageId: String, val block: PageBlock.Browser)
internal data class CloudNativeFile(val index: Int, val name: String, val bytes: Long)
internal data class CloudNativeInvitation(val key: String, val id: String, val guest: String, val title: String, val profile: JSONObject)
internal data class CloudCachedFile(val file: File, val path: String)

/** UI state is native; the optional provider owns signaling and the data channel. */
@Stable internal class CloudNativeSession(private val model: AppViewModel, val panel: CloudNativePanel,
    private val scope: CoroutineScope) {
    var command: (JSONObject) -> Unit = {}
    var acceptsInvitations = false
    var incomingContent by mutableStateOf(false); private set
    var status by mutableStateOf("正在连接"); private set
    var url by mutableStateOf<String?>(null); private set
    var title by mutableStateOf(""); private set
    var kind by mutableStateOf(""); private set
    var files by mutableStateOf(emptyList<CloudNativeFile>()); private set
    var peers by mutableStateOf(emptyList<FacePeer>()); private set
    var invitation by mutableStateOf<CloudNativeInvitation?>(null); private set
    var request by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var profile by mutableStateOf<JSONObject?>(null); private set
    var savingContact by mutableStateOf(false); private set
    val cached = mutableStateMapOf<Int, CloudCachedFile>()
    val saved = mutableStateListOf<Int>()
    private val queue = ArrayDeque<Int>()
    private val saving = mutableSetOf<Int>()
    private var pending: Int? = null
    private var contactJob: Job? = null
    val root get() = model.pluginManager.paths.storage(panel.owner).file(panel.block.root!!.path)
    val sending get() = File(root, "share.json").isFile
    init { if (sending) runCatching { manifest(JSONObject(File(root, "share.json").readText())) } }
    fun event(input: JSONObject) {
        when (input.optString("type")) {
            "status" -> status = input.optString("text").take(240)
            "share" -> url = CloudShareLinks.parse(input.optString("url"))
            "devices" -> peers = input.optJSONArray("peers")?.let { list -> (0 until minOf(64, list.length())).mapNotNull { i ->
                val p = list.optJSONObject(i) ?: return@mapNotNull null
                val id = p.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{22}")) } ?: return@mapNotNull null
                FacePeer(id, p.optString("name").take(40), port = 0, token = "", avatar = p.optString("avatar").substringAfter("base64,", "").take(16000), avatarFrame = p.optString("avatarFrame", "circle").take(64))
            } }.orEmpty()
            "incoming" -> if (acceptsInvitations) {
                val key = input.optString("key"); val id = input.optString("id"); val guest = input.optString("guest")
                if (key.matches(Regex("[A-Za-z0-9_-]{43}")) && CloudShareLinks.parse("https://share.kigtts.lhtstudio.com/kigtts/share/$id#$guest") != null)
                    invitation = CloudNativeInvitation(key, id, guest, input.optString("title").take(160), input.optJSONObject("profile") ?: JSONObject())
            }
            "request" -> request = input.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{16}")) }
            "request-complete" -> if (request == input.optString("id")) request = null
            "manifest" -> { manifest(input.getJSONObject("value")); files.firstOrNull { it.name == "profile.json" && it.bytes <= 393216 }?.let { get(it.index) } }
            "reset" -> { close(); incomingContent = true; files = emptyList(); profile = null; saved.clear(); queue.clear(); saving.clear(); pending = null; busy = false; savingContact = false }
            "progress" -> progress = input.optDouble("value", 0.0).toFloat().coerceIn(0f, 1f)
            "busy" -> { busy = input.optBoolean("value"); if (!busy) { pending = null; next(); finishContact() } }
        }
    }
    private fun manifest(value: JSONObject) {
        require(value.optInt("schema") == 1)
        title = value.optString("title").take(160); kind = value.optString("kind")
        val list = value.getJSONArray("files"); require(list.length() in 1..160)
        files = (0 until list.length()).map { index -> val f = list.getJSONObject(index)
            val name = f.getString("name"); require(name.length in 1..160 && !name.any { it == '/' || it == '\\' || it == ':' || it.isISOControl() })
            val bytes = f.getLong("bytes"); require(bytes in 1..512L*1024*1024)
            CloudNativeFile(index, name, bytes)
        }
        if (incomingContent && kind != "profile") files.firstOrNull { it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) }?.takeIf { it.bytes <= 20_000_000 }?.let { get(it.index) }
    }
    fun send(peer: FacePeer) { command(JSONObject().put("op", "offer").put("id", peer.id)) }
    fun decide(allow: Boolean) { request?.let { command(JSONObject().put("op", "decision").put("id", it).put("accept", allow)) }; request = null }
    fun accept() { invitation?.let { command(JSONObject().put("op", "connect").put("id", it.id).put("guest", it.guest).put("acceptKey", it.key)) }; invitation = null }
    fun reject() { invitation = null }
    fun get(index: Int, save: Boolean = false) {
        if (save) saving += index
        if (cached.containsKey(index)) { if (save) save(index); return }
        if (index != pending && index !in queue && files.any { it.index == index }) queue.addLast(index)
        next()
    }
    private fun next() {
        if (busy || pending != null || queue.isEmpty()) return
        val index = queue.removeFirst(); pending = index; busy = true; progress = 0f
        command(JSONObject().put("op", "get").put("index", index))
    }
    fun completed(file: File, path: String, key: String?) {
        val index = key?.toIntOrNull()
        if (index == null || index != pending || files.none { it.index == index && it.bytes == file.length() }) { file.delete(); return }
        cached[index] = CloudCachedFile(file, path)
        if (files[index].name == "profile.json") scope.launch {
            runCatching { withContext(Dispatchers.IO) { require(file.length() <= 393216); JSONObject(file.readText()) } }.getOrNull()?.let {
                // Public web manifests use "file"; the native profile model uses "image".
                val tiles = it.optJSONArray("tiles")
                repeat(minOf(24, tiles?.length() ?: 0)) { i ->
                    tiles?.optJSONObject(i)?.let { tile ->
                        tile.optString("file").takeIf { name -> files.any { entry -> entry.name == name } }
                            ?.let { name -> tile.put("image", name) }
                    }
                }
                profile = it
                files.firstOrNull { entry -> entry.name == it.optString("banner") && entry.bytes <= 20_000_000 }?.let { entry -> get(entry.index) }
            }
        }
        if (index in saving) save(index)
    }
    private fun save(index: Int) {
        val source = cached.remove(index) ?: return
        saving -= index; saved += index
        model.sharing.received(panel.owner, source.file, source.path)
    }
    fun saveContact() {
        if (profile == null || savingContact) return
        savingContact = true
        val names = profileAssetNames()
        files.filter { it.name in names && it.bytes <= 20_000_000 }.forEach { get(it.index) }
        finishContact()
    }
    private fun profileAssetNames(): Set<String> = profile?.let { p ->
        setOf(p.optString("banner")) + com.kigtts.next.identity.profileTiles(p).mapNotNull { it.image }
    }.orEmpty()
    private fun finishContact() {
        if (!savingContact || contactJob?.isActive == true || busy || pending != null || queue.isNotEmpty()) return
        val p = profile ?: return
        val entries = files.filter { it.name in profileAssetNames() }
        if (entries.any { it.index !in cached }) { savingContact = false; model.message("部分名片图片未收到，请重试"); return }
        contactJob = scope.launch {
            try {
                val cover = entries.firstOrNull { it.name == p.optString("banner") }?.let { cached[it.index]?.file }
                val images = com.kigtts.next.identity.profileTiles(p).mapNotNull { tile ->
                    entries.firstOrNull { it.name == tile.image }?.let { cached[it.index]?.file }?.let { tile.id to it }
                }.toMap()
                withContext(Dispatchers.IO) { com.kigtts.next.identity.ContactFavorites.get(model.getApplication()).save(p, cover, images) }
                model.message("已加入扩列收藏夹")
            } catch (_: Exception) { model.message("名片未能保存，请重试") }
            finally { savingContact = false }
        }
    }
    fun close() { contactJob?.cancel(); contactJob = null; cached.values.forEach { it.file.delete() }; cached.clear() }
}
