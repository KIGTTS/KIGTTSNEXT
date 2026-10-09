package com.kigtts.plugin.api

import kotlinx.serialization.Serializable

/** A scoped resource reference. An explicit owner allows a service to show provider metadata. */
@Serializable
data class PluginResource(val path: String, val pluginId: String? = null)

/** Rendered by the host; plugins supply data and commands, never Compose or their own styles. */
@Serializable
data class ManagementToggle(val checked: Boolean, val action: String)
@Serializable
data class ManagementItem(val id: String, val title: String, val subtitle: String = "",
    val icon: PluginIcon = PluginIcon.INFO, val image: PluginResource? = null,
    val selected: Boolean = false, val pinned: Boolean = false, val group: String = "",
    val primaryAction: String? = null, val actions: List<PageAction> = emptyList(), val toggle: ManagementToggle? = null)

@Serializable
data class ManagementFilter(val id: String, val label: String, val action: String, val selected: Boolean = false)
@Serializable
data class ManagementList(val id: String, val items: List<ManagementItem>,
    val reorderAction: String? = null, val emptyText: String = "暂无条目", val filters: List<ManagementFilter> = emptyList()) {
    init { require(items.size <= 1000 && items.map { it.id }.distinct().size == items.size) }
}

/** Keeps installed/new entries deterministic while retaining a saved order for returning IDs. */
object ItemOrder {
    fun reconcile(saved: List<String>, current: List<String>): List<String> =
        saved.filter { it in current }.distinct() + current.filter { it !in saved }.distinct()
    fun move(order: List<String>, from: String, to: String): List<String> {
        val result = order.toMutableList(); val source = result.indexOf(from); val target = result.indexOf(to)
        if (source < 0 || target < 0 || source == target) return order
        result.add(target, result.removeAt(source)); return result
    }
    fun validate(order: List<String>, current: List<String>) {
        require(order.size == current.size && order.distinct().size == order.size && order.toSet() == current.toSet()) { "列表内容已变化，请重新排序" }
    }
}
