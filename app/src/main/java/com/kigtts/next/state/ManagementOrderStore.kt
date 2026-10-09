package com.kigtts.next.state

import android.content.Context
import androidx.core.content.edit
import com.kigtts.plugin.api.ItemOrder
import com.kigtts.plugin.api.PluginJson

internal class ManagementOrderStore(context: Context) {
    private val preferences = context.getSharedPreferences("management-order", Context.MODE_PRIVATE)
    fun ordered(key: String, current: List<String>): List<String> {
        val saved = runCatching { PluginJson.decodeFromString<List<String>>(preferences.getString(key, "[]")!!) }.getOrDefault(emptyList())
        return ItemOrder.reconcile(saved, current)
    }
    fun save(key: String, order: List<String>, current: List<String>) {
        ItemOrder.validate(order, current)
        preferences.edit { putString(key, PluginJson.encodeToString(order)) }
    }
}
