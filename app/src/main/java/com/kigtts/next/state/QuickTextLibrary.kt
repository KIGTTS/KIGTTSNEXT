package com.kigtts.next.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable item identities and ordered groups survive edits, duplicates and restarts. */
class QuickTextLibrary(private val store: QuickPhraseStore) {
    private val itemState = MutableStateFlow(store.load())
    private val groupState = MutableStateFlow(store.loadCategories(itemState.value))
    private val iconState = MutableStateFlow(store.loadCategoryIcons(groupState.value))
    val phrases = itemState.asStateFlow()
    val categories = groupState.asStateFlow()
    val categoryIcons = iconState.asStateFlow()

    private fun save() = store.save(itemState.value, groupState.value, iconState.value)
    fun add(text: String, category: String, description: String = "") {
        if (text.isBlank() || category !in groupState.value) return
        itemState.value += QuickPhrase(text.trim(), category, description = description.trim())
        save()
    }
    fun edit(id: String, text: String, description: String, category: String) {
        if (text.isBlank() || category !in groupState.value) return
        itemState.value = itemState.value.map { if (it.id == id) it.copy(text = text.trim(), description = description.trim(), category = category) else it }
        save()
    }
    fun remove(id: String) {
        itemState.value = itemState.value.filterNot { it.id == id }
        save()
    }
    fun addCategory(name: String, icon: CategoryIcon = CategoryIcon.LIST): Boolean {
        val value = name.trim()
        if (value.isBlank() || value in groupState.value) return false
        iconState.value += value to icon
        groupState.value += value
        save()
        return true
    }
    fun movePhrase(id: String, targetId: String) {
        val all = itemState.value
        val source = all.firstOrNull { it.id == id } ?: return
        val target = all.firstOrNull { it.id == targetId } ?: return
        if (source.category != target.category || id == targetId) return
        val ordered = all.filter { it.category == source.category }.toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == targetId }
        ordered.add(to, ordered.removeAt(from))
        var index = 0
        itemState.value = all.map { if (it.category == source.category) ordered[index++] else it }
        save()
    }
    fun editCategory(original: String, name: String, icon: CategoryIcon): Boolean {
        val value = name.trim()
        if (original !in groupState.value || value.isBlank() || (value != original && value in groupState.value)) return false
        itemState.value = itemState.value.map { if (it.category == original) it.copy(category = value) else it }
        groupState.value = groupState.value.map { if (it == original) value else it }
        iconState.value = (iconState.value - original) + (value to icon)
        save()
        return true
    }
    fun transferPhrase(id: String, category: String): Boolean {
        val item = itemState.value.firstOrNull { it.id == id } ?: return false
        if (category !in groupState.value || item.category == category) return false
        itemState.value = itemState.value.filterNot { it.id == id } + item.copy(category = category)
        save()
        return true
    }
    fun moveCategory(name: String, target: String) {
        val ordered = groupState.value.toMutableList()
        val from = ordered.indexOf(name); val to = ordered.indexOf(target)
        if (from < 0 || to < 0 || from == to) return
        ordered.add(to, ordered.removeAt(from))
        groupState.value = ordered
        save()
    }
}
