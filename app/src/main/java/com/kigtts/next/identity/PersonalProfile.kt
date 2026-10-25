package com.kigtts.next.identity

import org.json.JSONArray
import org.json.JSONObject

/** One person, with any number of independent card backgrounds. */
internal data class PersonalProfile(val nickname: String = "", val bio: String = "", val avatar: String? = null,
    val contacts: List<ContactLink> = emptyList()) {
    fun on(card: IdentityCard) = card.copy(nickname = nickname, bio = bio, avatar = avatar, contacts = contacts)
    fun json() = JSONObject().put("nickname", nickname).put("bio", bio).put("avatar", avatar ?: "")
        .put("contacts", JSONArray(contacts.map { JSONObject().put("label", it.label).put("value", it.value) }))
    companion object {
        fun from(card: IdentityCard) = PersonalProfile(card.nickname, card.bio, card.avatar, card.contacts)
        fun from(json: JSONObject): PersonalProfile {
            val entries = json.optJSONArray("contacts") ?: JSONArray()
            return PersonalProfile(json.optString("nickname"), json.optString("bio"), json.optString("avatar").takeIf(String::isNotBlank),
                (0 until minOf(entries.length(), 8)).map { index -> entries.getJSONObject(index).let { ContactLink(it.optString("label"), it.optString("value")) } })
        }
    }
}
