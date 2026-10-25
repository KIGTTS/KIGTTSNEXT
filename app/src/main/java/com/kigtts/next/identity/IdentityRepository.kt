package com.kigtts.next.identity

import android.content.Context
import com.kigtts.next.cards.CardRepository
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ContactLink(val label: String, val value: String)
data class IdentityCard(val id: String = UUID.randomUUID().toString(), val designId: String,
    val nickname: String = "", val bio: String = "", val avatar: String? = null,
    val contacts: List<ContactLink> = emptyList()) {
    fun publicJson() = JSONObject().put("id", id).put("nickname", nickname.ifBlank { "KIGTTS 用户" })
        .put("bio", bio).put("contacts", JSONArray(contacts.map { JSONObject().put("label", it.label).put("value", it.value) }))
}

/** Personal cards use the same durable image/layer catalog and editor as the album. */
class IdentityRepository(context: Context, private val cards: CardRepository) {
    private val preferences = context.getSharedPreferences("personal-cards", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load()); val items = mutable.asStateFlow()
    private val selected = MutableStateFlow(preferences.getString("selected", null)); val selectedId = selected.asStateFlow()
    val current get() = mutable.value.firstOrNull { it.id == selected.value } ?: mutable.value.firstOrNull()
    private fun load(): List<IdentityCard> = runCatching {
        val list = JSONArray(preferences.getString("items", "[]"))
        (0 until list.length()).map { index -> val item = list.getJSONObject(index)
            val contacts = item.optJSONArray("contacts") ?: JSONArray()
            IdentityCard(item.getString("id"), item.getString("designId"), item.optString("nickname"), item.optString("bio"),
                item.optString("avatar").takeIf { it.isNotBlank() }, (0 until contacts.length()).map { n ->
                    contacts.getJSONObject(n).let { ContactLink(it.optString("label"), it.optString("value")) }
                })
        }
    }.getOrDefault(emptyList())
    @Synchronized fun create(): IdentityCard {
        require(mutable.value.size < 24) { "最多保存 24 张个人名片" }
        val stickers = cards.stickers
        synchronized(stickers) {
            if (stickers.frameStyles.value.none { it.id == "identity.banner" })
                stickers.apply(listOf(StickerEdit.PutFrameStyle(PolaroidFrameStyle("identity.banner", "个人名片", format = PolaroidFormat.WIDE))))
            val album = cards.albums.value.firstOrNull { it.id == "personal-cards" } ?: cards.createAlbum("我的名片", "personal-cards")
            val design = cards.create(PolaroidCard(title = "", frameColor = 0xFF245E63, frameStyleId = "identity.banner"), album.id)
            return IdentityCard(designId = design.id).also { save(it); select(it.id) }
        }
    }
    @Synchronized fun save(item: IdentityCard) {
        require(item.nickname.length <= 40 && item.bio.length <= 240 && item.contacts.size <= 8)
        require(item.contacts.all { it.label.length <= 24 && it.value.length <= 512 })
        val next = (mutable.value.filterNot { it.id == item.id } + item).let { values ->
            val order = mutable.value.map { it.id } + item.id; values.sortedBy { order.indexOf(it.id) }
        }
        persist(next); mutable.value = next
    }
    @Synchronized fun remove(id: String) { val next = mutable.value.filterNot { it.id == id }; persist(next); mutable.value = next }
    fun select(id: String) { selected.value = id; preferences.edit().putString("selected", id).apply() }
    private fun persist(values: List<IdentityCard>) {
        val json = JSONArray(values.map { it.publicJson().put("nickname", it.nickname).put("designId", it.designId).put("avatar", it.avatar ?: "") })
        check(preferences.edit().putString("items", json.toString()).commit()) { "名片未能保存，请重试" }
    }
}
