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
    val contacts: List<ContactLink> = emptyList(), val gradient: Boolean = false, val gradientColor: Long = 0xFF000000,
    val pattern: String = "", val avatarFrame: String = "circle", val cardFrameId: String = "none",
    val tags: List<String> = emptyList(), internal val tiles: List<ProfileTile> = emptyList()) {
    fun publicJson() = JSONObject().put("id", id).put("identityId", id).put("nickname", nickname.ifBlank { "KIGTTS 用户" })
        .put("bio", bio).put("avatarFrame", avatarFrame).put("avatarClipPath", AvatarFrames.svgPath(avatarFrame)).put("contacts", JSONArray(contacts.map { JSONObject().put("label", it.label).put("value", it.value).put("qrContent", it.qrContent) }))
        .put("tags", JSONArray(tags)).put("tiles", profileTileJson(tiles))
}

/** One personal profile and one editable design. Legacy presets are archived without deleting their artwork. */
class IdentityRepository(context: Context, private val cards: CardRepository) {
    private val preferences = context.getSharedPreferences("personal-cards", Context.MODE_PRIVATE)
    private val saved = load()
    private var profile = runCatching { preferences.getString("profile", null)?.let { PersonalProfile.from(JSONObject(it)) } }.getOrNull()
        ?: (saved.firstOrNull { it.id == preferences.getString("selected", null) } ?: saved.firstOrNull())?.let(PersonalProfile::from) ?: PersonalProfile()
    private val retained = saved.firstOrNull { it.id == preferences.getString("selected", null) } ?: saved.firstOrNull()
    private val mutable = MutableStateFlow(listOfNotNull(retained?.let(profile::on))); val items = mutable.asStateFlow()
    private val selected = MutableStateFlow(retained?.id); val selectedId = selected.asStateFlow()
    val current get() = mutable.value.firstOrNull { it.id == selected.value } ?: mutable.value.firstOrNull()
    init {
        cards.stickers.identityDesigns.migrate(saved, cards)
        if (saved.size > 1 && !preferences.contains("legacy-backgrounds-v2")) {
            check(preferences.edit().putString("legacy-backgrounds-v2", preferences.getString("items", "[]")).commit())
        }
        if (saved.isNotEmpty()) persist(mutable.value, profile)
    }
    private fun load(): List<IdentityCard> = runCatching {
        val list = JSONArray(preferences.getString("items", "[]"))
        (0 until list.length()).map { index -> val item = list.getJSONObject(index)
            val info = PersonalProfile.from(item)
            info.on(IdentityCard(item.getString("id"), item.getString("designId"), gradient = item.optBoolean("gradient", false),
                gradientColor = item.optLong("gradientColor", 0xFF000000), pattern = item.optString("pattern"), cardFrameId = item.optString("cardFrameId", "none")))
        }
    }.getOrDefault(emptyList())
    @Synchronized fun create(): IdentityCard {
        current?.let { return it }
        val stickers = cards.stickers
        synchronized(stickers) {
            if (stickers.frameStyles.value.none { it.id == "identity.banner" })
                stickers.apply(listOf(StickerEdit.PutFrameStyle(PolaroidFrameStyle("identity.banner", "个人名片", format = PolaroidFormat.WIDE))))
            val design = stickers.identityDesigns.put(PolaroidCard(title = "", frameColor = 0xFF245E63,
                frameStyleId = "identity.banner", photoBackdrop = PhotoBackdropMode.BLUR))
            return profile.on(IdentityCard(designId = design.id)).also { save(it); select(it.id) }
        }
    }
    @Synchronized fun save(item: IdentityCard) {
        require(item.nickname.length <= 40 && item.bio.length <= 240 && item.contacts.size <= 8)
        require(item.tags.size <= 12 && item.tags.all { it.isNotBlank() && it.length <= 20 })
        require(item.tiles.size <= 24 && item.tiles.distinctBy { it.id }.size == item.tiles.size)
        require(item.tiles.all { it.columns in 1..4 && it.rows in 1..3 && it.title.length <= 40 && it.text.length <= 2048 && it.contact.length <= 2048 })
        require(item.contacts.all { it.label.length <= 24 && it.value.length <= 2048 && it.value.isNotBlank() && (it.qrContent?.length ?: 0) <= 2048 })
        require(current == null || current?.id == item.id) { "个人名片只保留一张" }
        val nextProfile = PersonalProfile.from(item)
        val next = if (mutable.value.any { it.id == item.id }) mutable.value.map { if (it.id == item.id) item else it } else mutable.value + item
        val projected = next.map(nextProfile::on)
        persist(projected, nextProfile); profile = nextProfile; mutable.value = projected
    }
    @Synchronized fun remove(id: String) {
        val removed = mutable.value.firstOrNull { it.id == id }
        val next = mutable.value.filterNot { it.id == id }; persist(next, profile); mutable.value = next
        removed?.let { cards.stickers.identityDesigns.remove(it.designId) }
        if (selected.value == id) { selected.value = next.firstOrNull()?.id; preferences.edit().putString("selected", selected.value).apply() }
    }
    fun select(id: String) { if (mutable.value.none { it.id == id }) return; selected.value = id; preferences.edit().putString("selected", id).apply() }
    private fun persist(values: List<IdentityCard>, person: PersonalProfile) {
        val json = JSONArray(values.map { JSONObject().put("id", it.id).put("designId", it.designId).put("gradient", it.gradient).put("gradientColor", it.gradientColor).put("pattern", it.pattern).put("cardFrameId", it.cardFrameId) })
        val edit = preferences.edit().putString("items", json.toString()).putString("profile", person.json().toString()).putString("selected", values.firstOrNull()?.id)
        // Preserve earlier per-card information when consolidating the selected identity.
        if (!preferences.contains("profile") && !preferences.contains("legacy-items-v1")) edit.putString("legacy-items-v1", preferences.getString("items", "[]"))
        check(edit.commit()) { "名片未能保存，请重试" }
    }
}
