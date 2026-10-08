package com.kigtts.next.ui.cards

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ContactCard(val title: String, val description: String, val imageUri: String? = null)
class CardStore(context: Context) {
    private val preferences = context.getSharedPreferences("contact-cards", Context.MODE_PRIVATE)
    fun load(): List<ContactCard> = runCatching {
        val array = JSONArray(preferences.getString("items", null) ?: return listOf(ContactCard("名片", "这里显示名片"), ContactCard("关于我", "点击右下角添加你的名片")))
        List(array.length()) { i -> array.getJSONObject(i).let { ContactCard(it.getString("title"), it.getString("description"), it.optString("uri").takeIf(String::isNotBlank)) } }
    }.getOrDefault(emptyList())
    fun save(cards: List<ContactCard>) {
        val json = JSONArray()
        cards.forEach { json.put(JSONObject().put("title", it.title).put("description", it.description).put("uri", it.imageUri ?: "")) }
        preferences.edit().putString("items", json.toString()).apply()
    }
}
