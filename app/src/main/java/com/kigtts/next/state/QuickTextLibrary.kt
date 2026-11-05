package com.kigtts.next.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable item identities and ordered groups survive edits, duplicates and restarts. */
class QuickTextLibrary(private val store: QuickPhraseStore) {
    private val itemState = MutableStateFlow(store.load())
    private val groupState = MutableStateFlow(store.loadCategories(itemState.value))
    private val iconState = MutableStateFlow(store.loadCategoryIcons(groupState.value))
    private val idState=MutableStateFlow(store.groupIds(groupState.value))
    private val revisionState=MutableStateFlow(store.revision())
    val phrases = itemState.asStateFlow()
    val categories = groupState.asStateFlow()
    val categoryIcons = iconState.asStateFlow()
    val categoryIds=idState.asStateFlow()
    val revision=revisionState.asStateFlow()
    fun receipt(key:String)=store.receipt(key)
    fun receipt(key:String,value:String)=store.receipt(key,value)

    private fun save() {
        idState.value=groupState.value.associateWith { idState.value[it] ?: java.util.UUID.randomUUID().toString() }
        val next=revisionState.value+1;store.save(itemState.value,groupState.value,iconState.value,idState.value,next);revisionState.value=next
    }
    /** Validation happens before this one durable commit; UI state publishes only after persistence. */
    fun commit(items:List<QuickPhrase>,groups:List<String>,icons:Map<String,CategoryIcon>,ids:Map<String,String>,expected:Long,receipt:Pair<String,String>) {
        check(revisionState.value==expected) { "快捷文本已更新，请重新预览" }
        val next=expected+1;store.save(items,groups,icons,ids,next,receipt)
        itemState.value=items;groupState.value=groups;iconState.value=icons;idState.value=ids;revisionState.value=next
    }
    fun savePhrase(phrase: QuickPhrase): Boolean {
        if (phrase.category !in groupState.value || (phrase.text.isBlank() &&
            (phrase.cardType() == QuickCardType.TEXT || phrase.mediaId.isNullOrBlank()))) return false
        val value = phrase.copy(title = phrase.title.trim(), formats = com.kigtts.next.text.TextFormatting.normalize(phrase.formats, phrase.text.length))
        itemState.value = if (itemState.value.any { it.id == phrase.id }) itemState.value.map { if (it.id == phrase.id) value else it } else itemState.value + value
        save(); return true
    }
    fun add(text: String, category: String, title: String = "", mediaId: String? = null) {
        if (text.isBlank() || category !in groupState.value) return
        itemState.value += QuickPhrase(text.trim(), category, title = title.trim(), mediaId = mediaId)
        save()
    }
    fun edit(id: String, text: String, title: String, category: String, mediaId: String? = itemState.value.firstOrNull { it.id == id }?.mediaId) {
        if (text.isBlank() || category !in groupState.value) return
        itemState.value = itemState.value.map { if (it.id == id) it.copy(text = text.trim(), title = title.trim(), category = category, mediaId = mediaId,
            formats = com.kigtts.next.text.TextFormatting.rebase(it.formats, it.text, text.trim())) else it }
        save()
    }
    fun remove(id: String) {
        itemState.value = itemState.value.filterNot { it.id == id }
        save()
    }
    fun detachMedia(id: String) { itemState.value=itemState.value.map { if(it.mediaId==id)it.copy(mediaId=null) else it };save() }
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
        idState.value=(idState.value-original)+(value to requireNotNull(idState.value[original]))
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
    fun removeCategory(name: String): Boolean {
        if (name !in groupState.value || groupState.value.size <= 1) return false
        itemState.value = itemState.value.filterNot { it.category == name }
        groupState.value -= name; iconState.value -= name; idState.value -= name
        save(); return true
    }
}
