package com.kigtts.next.overlay

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OverlayShortcut(
    val id: String, val title: String, val symbol: String,
    val target: String = "home", val component: String? = null,
    val owner: String? = null, val publisher: String? = null, val action: String? = null,
    val smallWindow: Boolean = false, val showLabel: Boolean = true,
    val category: String = "KIGTTS", val enabled: Boolean = true, val badge: OverlayBadge? = null,
    val iconPath: String? = null,
)

@Serializable data class OverlayBadge(val kind: String = "dot", val tone: String = "primary", val count: Int = 0)

@Serializable
data class OverlaySettings(
    val enabled: Boolean = false, val autoHide: Boolean = true,
    val rightEdge: Boolean = true, val position: Float = .55f,
    val shortcuts: List<OverlayShortcut> = defaultOverlayShortcuts(),
    val showLabels: Boolean = true,
    val preferSmallWindows: Boolean = true,
)

fun defaultOverlayShortcuts() = listOf(
    OverlayShortcut("home", "字幕板", "subtitles"),
    OverlayShortcut("camera", "拍照", "photo_camera", "camera"),
    OverlayShortcut("cards", "拍立得", "photo_library", "cards"),
    OverlayShortcut("sketch", "画板", "draw", "sketch"),
)

/** Collapse the two legacy routes in their first saved position, retaining every other entry. */
internal fun normalizeOverlayShortcuts(items: List<OverlayShortcut>): List<OverlayShortcut> {
    var subtitleSeen = false
    return items.mapNotNull { item ->
        if (item.owner == null && item.component == null && item.id in setOf("home", "quick-text")) {
            if (subtitleSeen) null else {
                subtitleSeen = true
                item.copy(id = "home", title = "字幕板", symbol = "subtitles", target = "home")
            }
        } else item
    }.distinctBy(OverlayShortcut::id).take(100)
}

/** One process-wide observable store. No activity or worker is retained by the overlay. */
class OverlayStore private constructor(context: Context) {
    private val application = context.applicationContext
    private val prefs = context.getSharedPreferences("native-overlay", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(read())
    val settings = mutable.asStateFlow()
    private fun read(): OverlaySettings = runCatching {
        json.decodeFromString<OverlaySettings>(prefs.getString("settings", null) ?: "{}")
    }.getOrDefault(OverlaySettings()).let { it.copy(position = it.position.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .55f,
        shortcuts = normalizeOverlayShortcuts(it.shortcuts)) }
    @Synchronized fun update(block: (OverlaySettings) -> OverlaySettings) {
        val next = block(mutable.value).let { it.copy(position = it.position.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .55f, shortcuts = normalizeOverlayShortcuts(it.shortcuts)) }
        if (next == mutable.value) return
        prefs.edit().putString("settings", json.encodeToString(next)).apply()
        mutable.value = next
    }
    fun move(id: String, delta: Int) = update { state ->
        val items = state.shortcuts.toMutableList(); val from = items.indexOfFirst { it.id == id }
        if (from >= 0) {
            val target = (from + delta).coerceIn(0, items.lastIndex)
            if (target != from) items.add(target, items.removeAt(from))
        }
        state.copy(shortcuts = items)
    }
    fun order(ids: List<String>) = update { state ->
        if (ids.size != state.shortcuts.size || ids.toSet() != state.shortcuts.map { it.id }.toSet()) state
        else state.copy(shortcuts = ids.map(state.shortcuts.associateBy { it.id }::getValue))
    }
    companion object {
        @Volatile private var instance: OverlayStore? = null
        fun get(context: Context): OverlayStore = instance?.takeIf { it.application === context.applicationContext } ?: synchronized(this) {
            instance?.takeIf { it.application === context.applicationContext } ?: OverlayStore(context.applicationContext).also { instance = it }
        }
    }
}
