package com.kigtts.next.sharing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A revocable capability URL and per-session receive consent, never an open file browser. */
internal class FacePhotoSession(val root: File, private val profile: () -> JSONObject,
    private val contactIcon: (com.kigtts.next.identity.ContactLink) -> String = { "<span aria-hidden=\"true\">↗</span>" },
    private val received: (FaceOffer, List<FaceAsset>) -> Unit) : Closeable {
    val token = faceToken()
    // Discovery may expose a receive endpoint, never the browser's unconfirmed download capability.
    val peerToken = faceToken()
    val assets = ConcurrentHashMap<String, FaceAsset>()
    private val tickets = ConcurrentHashMap<String, FaceOffer>()
    val hasActiveOffers get() = tickets.values.any { it.status in setOf("waiting", "accepted", "receiving") }
    private val consent = ConcurrentHashMap.newKeySet<String>()
    private val pending = MutableStateFlow<List<FaceOffer>>(emptyList()); val requests = pending.asStateFlow()
    private val guard = Any()
    @Volatile private var closed = false
    private var disposed = false
    val server = LocalPhotoServer(::handle)
    val path get() = "/s/$token/"
    init { root.mkdirs() }
    fun approve(id: String, auto: Boolean) = synchronized(guard) {
        val ticket = tickets[id]?.takeIf { it.status == "waiting" } ?: return@synchronized
        if (auto) consent += ticket.session
        tickets[id] = ticket.copy(status = "accepted"); refresh()
    }
    fun reject(id: String) = synchronized(guard) { tickets[id]?.let { tickets[id] = it.copy(status = "rejected") }; refresh() }
    fun stopAutoReceive() { consent.clear() }
    fun revoke() { closed = true; server.close() }
    private fun refresh() { pending.value = tickets.values.filter { it.status == "waiting" }.sortedBy { it.created } }
    private fun handle(request: PhotoHttpRequest, response: PhotoHttpResponse) {
        val peerPath = "/s/$peerToken/"
        val peerRequest = request.path.startsWith(peerPath)
        val prefix = if (peerRequest) peerPath else path
        if (closed || !request.path.startsWith(prefix)) { response.text("{}", status = 404); return }
        val route = request.path.removePrefix(prefix)
        if (peerRequest && !(route == "hello" || route == "offer" || route.startsWith("offer/") || route.startsWith("receive/"))) {
            response.text("{}", status = 404); return
        }
        when {
            request.method == "GET" && route.isEmpty() -> response.text(faceGallery(profile(), assets.values.sortedBy { it.name }, path, contactIcon), "text/html; charset=utf-8")
            request.method == "GET" && route == "hello" -> response.text(profile().put("protocol", 1).toString())
            request.method == "GET" && route == "theme" -> response.text(faceWebTheme(com.kigtts.next.media.WebThemeState.current).json().toString())
            request.method == "GET" && route.startsWith("file/") -> {
                val id = route.removePrefix("file/").substringBefore('?')
                val asset = assets[id]; val file = asset?.file
                if (file?.isFile != true) response.text("{}", status = 404)
                else response.file(file, asset.mime, request.path.endsWith("?download=1"))
            }
            request.method == "GET" && route.startsWith("download?ids=") -> {
                val ids = route.substringAfter("download?ids=").split(',').distinct()
                val selected = ids.map { requireNotNull(assets[it]) }; require(selected.size in 1..128)
                response.stream("application/zip", "KIGTTS-photos.zip") { output ->
                    val zip = ZipOutputStream(output)
                    selected.forEachIndexed { index, asset ->
                        zip.putNextEntry(ZipEntry("${index + 1}-${asset.variant}.${asset.file!!.extension}"))
                        asset.file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }; zip.finish()
                }
            }
            request.method == "POST" && route == "offer" -> {
                val json = JSONObject(String(request.body(393216), Charsets.UTF_8))
                val files = assetsFrom(json); val session = json.getString("session"); val id = json.getString("id")
                require(session.matches(Regex("[A-Za-z0-9_-]{32,64}")) && id.matches(Regex("[A-Za-z0-9_-]{32,64}")))
                val identity = json.getJSONObject("profile"); require(identity.toString().toByteArray(Charsets.UTF_8).size <= 262144)
                require(identity.optString("nickname").length <= 40 && identity.optString("bio").length <= 240)
                synchronized(guard) {
                    val now = System.currentTimeMillis()
                    tickets.entries.removeAll { (_, ticket) -> now - ticket.created > 30 * 60_000L }
                    tickets.replaceAll { _, ticket -> if (ticket.status == "waiting" && now - ticket.created > 180_000L) ticket.copy(status = "rejected") else ticket }
                    if (tickets.size >= 128) tickets.entries.filter { it.value.status in setOf("complete", "error", "rejected") }
                        .minByOrNull { it.value.created }?.let { tickets.remove(it.key) }
                    require(tickets.size < 128 || tickets.containsKey(id))
                    val old = tickets[id]
                    if (old != null) require(old.session == session && old.assets == files)
                    else {
                        require(tickets.values.count { it.status in setOf("waiting", "accepted", "receiving") } < 8)
                        tickets[id] = FaceOffer(id, session, identity, files, status = if (session in consent) "accepted" else "waiting")
                    }
                    refresh()
                    response.text(JSONObject().put("id", id).put("status", tickets.getValue(id).status).toString(), status = 202)
                }
            }
            request.method == "GET" && route.startsWith("offer/") -> {
                val ticket = tickets[route.removePrefix("offer/")] ?: return response.text("{}", status = 404)
                response.text(JSONObject().put("status", ticket.status).toString())
            }
            request.method == "PUT" && route.startsWith("receive/") -> receive(request, response, route)
            else -> response.text("{}", status = 404)
        }
    }
    private fun receive(request: PhotoHttpRequest, response: PhotoHttpResponse, route: String) {
        val parts = route.split('/'); require(parts.size == 3)
        val ticket = tickets[parts[1]] ?: return response.text("{}", status = 404)
        require(request.headers["x-kigtts-session"] == ticket.session)
        if (ticket.status !in setOf("accepted", "receiving", "complete")) return response.text("{}", status = 403)
        val asset = ticket.assets.firstOrNull { it.id == parts[2] } ?: return response.text("{}", status = 404)
        require(request.length == asset.bytes)
        val directory = File(root, ticket.id).apply { mkdirs() }
        val destination = File(directory, "${asset.id}.${extension(asset.mime)}")
        val stage = File.createTempFile("upload-", ".tmp", directory)
        try {
            stage.outputStream().use { output ->
                var remaining = asset.bytes; val buffer = ByteArray(65536)
                while (remaining > 0) {
                    val count = request.input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw EOFException(); output.write(buffer, 0, count); remaining -= count
                }
            }
            require(sha256(stage) == asset.sha)
            synchronized(guard) {
                check(!closed && tickets[ticket.id]?.status != "rejected")
                if (!destination.isFile) java.nio.file.Files.move(stage.toPath(), destination.toPath())
                val current = tickets.getValue(ticket.id)
                if (current.status != "complete") {
                    tickets[ticket.id] = current.copy(status = "receiving")
                    val all = ticket.assets.map { it.copy(file = File(directory, "${it.id}.${extension(it.mime)}")) }
                    if (all.all { it.file!!.isFile && it.file.length() == it.bytes }) {
                        try { received(ticket, all); tickets[ticket.id] = current.copy(status = "complete") }
                        catch (failure: Exception) { tickets[ticket.id] = current.copy(status = "error"); throw failure }
                    }
                }
            }
            response.text("{}")
        } finally { stage.delete() }
    }
    override fun close() = synchronized(guard) {
        if (disposed) return@synchronized
        disposed = true; revoke(); consent.clear(); tickets.clear(); pending.value = emptyList(); assets.clear()
        root.deleteRecursively()
    }
    companion object { fun extension(mime: String) = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" } }
}
