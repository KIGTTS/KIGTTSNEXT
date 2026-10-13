package com.kigtts.next.cards

import com.kigtts.plugin.api.*
import org.json.JSONArray
import org.json.JSONObject

/** Native provenance is separate from the stable plugin DTO and cannot be changed by a plugin. */
internal class PolaroidCardPermissions {
    private var locked: Set<String> = emptySet()
    fun load(json: JSONObject) {
        val values = json.optJSONArray("readOnlyCards") ?: JSONArray()
        stickerRequire(values.length() <= 4096, StickerError.TOO_LARGE, "接收照片记录过多")
        // Unknown permission schemas fail closed rather than silently making received cards editable.
        stickerRequire(!json.has("readOnlyCards") || json.optInt("readOnlyCardsSchema") == 1,
            message = "接收照片权限版本不支持")
        locked = (0 until values.length()).map { values.getString(it) }.toSet()
        stickerRequire(locked.all { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}")) }, message = "照片权限标识不合法")
    }
    fun current(): Set<String> = locked
    fun isReadOnly(id: String): Boolean = id in locked
    fun plan(additions: Set<String>, cards: List<PolaroidCard>): Set<String> {
        stickerRequire(additions.all { id -> cards.any { it.id == id } }, StickerError.NOT_FOUND, "接收照片不存在")
        return (locked + additions).also { stickerRequire(it.size <= 4096, StickerError.TOO_LARGE, "接收照片记录过多") }
    }
    fun check(before: StickerSnapshot, after: StickerSnapshot,
        beforeFrames: List<PolaroidFrameStyle>, afterFrames: List<PolaroidFrameStyle>) {
        val previous = before.cards.associateBy { it.id }
        fun frame(card: PolaroidCard, all: List<PolaroidFrameStyle>) = all.firstOrNull { it.id == card.frameStyleId }
            ?: PolaroidFrames.defaults.first()
        after.cards.filter { it.id in locked }.forEach { card ->
            val old = previous[card.id]
            stickerRequire(old == card && frame(card, beforeFrames) == frame(card, afterFrames),
                StickerError.PERMISSION_DENIED, "接收的照片只能查看和分享，不能编辑")
        }
    }
    fun write(json: JSONObject, next: Set<String>) {
        json.put("readOnlyCardsSchema", 1).put("readOnlyCards", JSONArray(next.sorted()))
    }
    fun commit(next: Set<String>) { locked = next }
}
