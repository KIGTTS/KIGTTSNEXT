package com.kigtts.next.identity

import android.content.Context
import com.kigtts.next.cards.CardRepository
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ContactLink(val label: String, val value: String, val qrContent: String? = null)
data class IdentityCard(val id: String = UUID.randomUUID().toString(), val designId: String,
    val nickname: String = "", val bio: String = "", val avatar: String? = null,
    val contacts: List<ContactLink> = emptyList(), val gradient: Boolean = false) {
    fun publicJson() = JSONObject().put("id", id).put("nickname", nickname.ifBlank { "KIGTTS 用户" })
        .put("bio", bio).put("contacts", JSONArray(contacts.map { JSONObject().put("label", it.label).put("value", it.value).put("qrContent", it.qrContent) }))
}

/** Background presets share a profile; album layers remain individually editable. */
class IdentityRepository(context: Context, private val cards: CardRepository) {
    private val preferences = context.getSharedPreferences("personal-cards", Context.MODE_PRIVATE)
    private val saved = load()
    private var profile = runCatching { preferences.getString("profile", null)?.let { PersonalProfile.from(JSONObject(it)) } }.getOrNull()
        ?: (saved.firstOrNull { it.id == preferences.getString("selected", null) } ?: saved.firstOrNull())?.let(PersonalProfile::from) ?: PersonalProfile()
    private val mutable = MutableStateFlow(saved.map(profile::on)); val items = mutable.asStateFlow()
    private val selected = MutableStateFlow(preferences.getString("selected", null)); val selectedId = selected.asStateFlow()
    val current get() = mutable.value.firstOrNull { it.id == selected.value } ?: mutable.value.firstOrNull()
    init { if (saved.isNotEmpty() && !preferences.contains("profile")) persist(mutable.value, profile) }
    private fun load(): List<IdentityCard> = runCatching {
        val list = JSONArray(preferences.getString("items", "[]"))
        (0 until list.length()).map { index -> val item = list.getJSONObject(index)
            val info = PersonalProfile.from(item)
            info.on(IdentityCard(item.getString("id"), item.getString("designId"), gradient = item.optBoolean("gradient", true)))
        }
    }.getOrDefault(emptyList())
    @Synchronized fun create(): IdentityCard {
        require(mutable.value.size < 24) { "最多保存 24 张名片背景" }
        val stickers = cards.stickers
        synchronized(stickers) {
            if (stickers.frameStyles.value.none { it.id == "identity.banner" })
                stickers.apply(listOf(StickerEdit.PutFrameStyle(PolaroidFrameStyle("identity.banner", "个人名片", format = PolaroidFormat.WIDE))))
            val album = cards.albums.value.firstOrNull { it.id == "personal-cards" } ?: cards.createAlbum("我的名片", "personal-cards")
            val design = cards.create(PolaroidCard(title = "", frameColor = 0xFF245E63, frameStyleId = "identity.banner"), album.id)
            return profile.on(IdentityCard(designId = design.id)).also { save(it); select(it.id) }
        }
    }
    @Synchronized fun save(item: IdentityCard) {
        require(item.nickname.length <= 40 && item.bio.length <= 240 && item.contacts.size <= 8)
        require(item.contacts.all { it.label.length <= 24 && it.value.length <= 2048 && it.value.isNotBlank() && (it.qrContent?.length ?: 0) <= 2048 })
        val nextProfile = PersonalProfile.from(item)
        val next = if (mutable.value.any { it.id == item.id }) mutable.value.map { if (it.id == item.id) item else it } else mutable.value + item
        val projected = next.map(nextProfile::on)
        persist(projected, nextProfile); profile = nextProfile; mutable.value = projected
    }
    @Synchronized fun remove(id: String) {
        val next = mutable.value.filterNot { it.id == id }; persist(next, profile); mutable.value = next
        if (selected.value == id) { selected.value = next.firstOrNull()?.id; preferences.edit().putString("selected", selected.value).apply() }
    }
    fun select(id: String) { if (mutable.value.none { it.id == id }) return; selected.value = id; preferences.edit().putString("selected", id).apply() }
    private fun persist(values: List<IdentityCard>, person: PersonalProfile) {
        val json = JSONArray(values.map { JSONObject().put("id", it.id).put("designId", it.designId).put("gradient", it.gradient) })
        val edit = preferences.edit().putString("items", json.toString()).putString("profile", person.json().toString())
        // Preserve earlier per-card information when consolidating the selected identity.
        if (!preferences.contains("profile") && !preferences.contains("legacy-items-v1")) edit.putString("legacy-items-v1", preferences.getString("items", "[]"))
        check(edit.commit()) { "名片未能保存，请重试" }
    }
}
