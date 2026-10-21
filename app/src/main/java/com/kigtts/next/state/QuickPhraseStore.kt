package com.kigtts.next.state

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class QuickPhraseStore(context: Context) {
    init { com.kigtts.next.icons.MaterialSymbolCatalog.initialize(context) }
    private val preferences = context.getSharedPreferences("quick-phrases", Context.MODE_PRIVATE)
    fun groupIds(categories:List<String>):Map<String,String> {
        val json=runCatching { JSONObject(preferences.getString("category-ids",null) ?: "{}") }.getOrDefault(JSONObject())
        val seen=mutableSetOf<String>()
        val ids=categories.associateWith { name -> json.optString(name).takeIf { it.matches(Regex("[a-f0-9-]{36}")) && seen.add(it) }
            ?: java.util.UUID.randomUUID().toString().also(seen::add) }
        preferences.edit().putString("category-ids",JSONObject(ids).toString()).apply();return ids
    }
    fun revision()=preferences.getLong("revision",0)
    fun receipt(key:String):String?=runCatching { JSONObject(preferences.getString("plugin-receipts",null) ?: "{}").optString(key).takeIf(String::isNotBlank) }.getOrNull()
    fun receipt(key:String,value:String) {val json=JSONObject(preferences.getString("plugin-receipts",null) ?: "{}");json.put(key,value)
        while(json.length()>64)json.remove(json.keys().next());preferences.edit().putString("plugin-receipts",json.toString()).apply()}
    fun load(): List<QuickPhrase> = runCatching {
        val json = JSONArray(preferences.getString("items", null) ?: "[]")
        if (!preferences.contains("items")) defaults else List(json.length()) { i -> QuickPhraseCodec.read(json.getJSONObject(i)) }
    }.getOrDefault(defaults).let { items ->
        val seen = mutableSetOf<String>()
        val stable = items.map { if (seen.add(it.id)) it else it.copy(id = java.util.UUID.randomUUID().toString().also(seen::add)) }
        save(stable, loadCategories(stable))
        stable
    }
    fun loadCategories(phrases: List<QuickPhrase>): List<String> = runCatching {
        val json = JSONArray(preferences.getString("categories", null) ?: "[\"常用\",\"生活\",\"收藏\",\"出行\"]")
        (List(json.length()) { json.getString(it) } + phrases.map { it.category }).filter(String::isNotBlank).distinct()
    }.getOrDefault(listOf("常用", "生活", "收藏", "出行") + phrases.map { it.category }).distinct()
    fun loadCategoryIcons(categories: List<String>): Map<String, CategoryIcon> {
        val json = runCatching { JSONObject(preferences.getString("category-icons", null) ?: "{}") }.getOrDefault(JSONObject())
        return categories.associateWith { category ->
            runCatching { CategoryIcon.valueOf(json.optString(category)) }.getOrDefault(CategoryIcon.defaultFor(category))
        }
    }
    fun save(phrases: List<QuickPhrase>, categories: List<String>, icons: Map<String, CategoryIcon> = loadCategoryIcons(categories),
        ids:Map<String,String> = groupIds(categories),revision:Long=revision(),receipt:Pair<String,String>?=null) {
        val json = JSONArray()
        phrases.forEach { json.put(QuickPhraseCodec.write(it)) }
        val categoryIcons = JSONObject()
        categories.forEach { categoryIcons.put(it, (icons[it] ?: CategoryIcon.defaultFor(it)).name) }
        preferences.edit().putString("items", json.toString()).putString("categories", JSONArray(categories).toString())
            .putString("category-icons", categoryIcons.toString()).putString("category-ids",JSONObject(ids).toString()).putLong("revision",revision).also { edit ->
                receipt?.let { (key,value) -> val receipts=JSONObject(preferences.getString("plugin-receipts",null) ?: "{}")
                    receipts.put(key,value);while(receipts.length()>64)receipts.remove(receipts.keys().next())
                    edit.putString("plugin-receipts",receipts.toString()) }
            }.apply()
    }
    private val defaults get() = listOf(
        QuickPhrase("你好，很高兴见到你。", "常用"), QuickPhrase("谢谢你的帮助。", "常用"),
        QuickPhrase("请稍等一下。", "常用"), QuickPhrase("可以再说一遍吗？", "常用"),
        QuickPhrase("我想喝一杯水。", "常用"), QuickPhrase("再见，祝你今天愉快。", "常用"),
        QuickPhrase("我需要一些帮助。", "生活"), QuickPhrase("请问洗手间在哪里？", "生活"),
        QuickPhrase("这个多少钱？", "出行"), QuickPhrase("我要去这个地址。", "出行"),
        QuickPhrase("这是我收藏的短语。", "收藏"),
    )
}
