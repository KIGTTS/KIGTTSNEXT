package com.kigtts.next.ui.cards

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

internal enum class CardManagementMode { GRID, STRIP, ALBUMS }

/** Selection belongs to the native browsing scene and survives rotation without writing user data. */
@Stable internal class CardManagementState {
    var mode by mutableStateOf<CardManagementMode?>(null); private set
    var albumId by mutableStateOf<String?>(null); private set
    var originId by mutableStateOf<String?>(null); private set
    var displayMode by mutableStateOf(CardManagementMode.GRID); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    val active get() = mode != null
    fun photos(album: String, id: String, grid: Boolean) {
        albumId = album; originId = id; displayMode = if (grid) CardManagementMode.GRID else CardManagementMode.STRIP
        mode = displayMode; selected = setOf(id)
    }
    fun albums(id: String?) { albumId = null; originId = id; displayMode = CardManagementMode.ALBUMS; mode = displayMode; selected = setOfNotNull(id) }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun selectAll(ids: List<String>) { selected = if (selected.size == ids.size && selected.containsAll(ids)) emptySet() else ids.toSet() }
    fun reconcile(ids: Collection<String>) { selected = selected.intersect(ids.toSet()) }
    fun clear() { selected = emptySet() }
    fun finish() { mode = null }
    companion object {
        val saver = Saver<CardManagementState, List<String>>(save = {
            listOf(it.mode?.name.orEmpty(), it.displayMode.name, it.albumId.orEmpty(), it.originId.orEmpty()) + it.selected
        }, restore = { value -> CardManagementState().apply {
            mode = CardManagementMode.entries.firstOrNull { it.name == value.firstOrNull() }
            displayMode = CardManagementMode.entries.firstOrNull { it.name == value.getOrNull(1) } ?: CardManagementMode.GRID
            albumId = value.getOrNull(2)?.takeIf { it.isNotBlank() }; originId = value.getOrNull(3)?.takeIf { it.isNotBlank() }
            selected = value.drop(4).toSet()
        } })
    }
}
@Composable internal fun rememberCardManagementState() = rememberSaveable(saver = CardManagementState.saver) { CardManagementState() }
