package com.kigtts.next.identity

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class SavedContact(val id: String, val profile: String, val savedAt: Long, val cover: File?)

/** Independent snapshots; ending a sharing session never revokes a saved contact. */
internal class ContactFavorites private constructor(context: Context) {
    private val root = File(context.filesDir, "contact-favorites").apply { mkdirs() }
    private val mutable = MutableStateFlow(load())
    val items = mutable.asStateFlow()

    private fun load() = root.listFiles().orEmpty().filter { it.extension == "json" }.take(1000).mapNotNull { file ->
        runCatching {
            require(file.length() <= 393216)
            val json = JSONObject(file.readText(Charsets.UTF_8))
            SavedContact(file.nameWithoutExtension, json.getJSONObject("profile").toString(), json.getLong("savedAt"),
                File(root, file.nameWithoutExtension + ".jpg").takeIf(File::isFile))
        }.getOrNull()
    }.sortedByDescending { it.savedAt }

    fun key(profile: JSONObject): String {
        val person = PublicIdentityProfile.from(profile)
        val source = profile.optString("identityId").take(128).takeIf(String::isNotBlank)
            ?: (person.name + "\n" + person.contacts.joinToString("\n") { it.label + ":" + it.value })
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    @Synchronized fun save(profile: JSONObject, cover: File? = null, images: Map<String, File> = emptyMap()): SavedContact {
        val id = key(profile)
        require(mutable.value.size < 1000 || mutable.value.any { it.id == id }) { "收藏夹已满，请先整理名片" }
        val snapshot = PublicIdentityProfile.from(profile).json()
        val tiles = profileTiles(snapshot).map { tile -> images[tile.id]?.let { file ->
            require(file.length() in 1..20_000_000)
            val bytes = file.readBytes()
            val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val target = File(root, "media/$key").apply { parentFile?.mkdirs() }
            if (!target.isFile) write(target, bytes)
            tile.copy(image = key)
        } ?: tile }
        snapshot.put("tiles", profileTileJson(tiles))
        profile.optString("identityId").take(128).takeIf(String::isNotBlank)?.let { snapshot.put("identityId", it) }
        (profile.opt("coverButtonsBaked") as? Boolean)?.let { snapshot.put("coverButtonsBaked", it) }
        val now = System.currentTimeMillis()
        if (cover?.isFile == true) {
            require(cover.length() in 1..20_000_000) { "名片图片过大" }
            write(File(root, "$id.jpg"), cover.readBytes())
        }
        write(File(root, "$id.json"), JSONObject().put("profile", snapshot).put("savedAt", now).toString().toByteArray(Charsets.UTF_8))
        val item = SavedContact(id, snapshot.toString(), now, File(root, "$id.jpg").takeIf(File::isFile))
        mutable.value = listOf(item) + mutable.value.filterNot { it.id == id }
        return item
    }
    fun image(id: String): File? = id.takeIf { it.matches(Regex("[0-9a-f]{64}")) }?.let { File(root, "media/$it").takeIf(File::isFile) }
    @Synchronized fun remove(id: String) {
        require(id.matches(Regex("[0-9a-f]{64}")))
        check(!File(root, "$id.json").exists() || File(root, "$id.json").delete()) { "未能移除名片" }
        File(root, "$id.jpg").delete()
        mutable.value = mutable.value.filterNot { it.id == id }
    }
    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    companion object {
        private val instances = java.util.WeakHashMap<Context, ContactFavorites>()
        @Synchronized fun get(context: Context): ContactFavorites {
            val app = context.applicationContext
            return instances.getOrPut(app) { ContactFavorites(app) }
        }
    }
}
