package com.kigtts.next.state

import com.kigtts.next.icons.MaterialSymbolCatalog

/** Existing keys remain portable strings. Symbols extend the same SDK13 icon field. */
class CategoryIcon private constructor(val name: String, val label: String, val symbol: String? = null) {
    override fun equals(other: Any?) = other is CategoryIcon && other.name == name
    override fun hashCode() = name.hashCode()
    override fun toString() = name
    companion object {
        val LIST = CategoryIcon("LIST", "列表"); val INFO = CategoryIcon("INFO", "常用")
        val PERSON = CategoryIcon("PERSON", "生活"); val FAVORITE = CategoryIcon("FAVORITE", "收藏")
        val HOME = CategoryIcon("HOME", "家庭"); val WORK = CategoryIcon("WORK", "工作")
        val SCHOOL = CategoryIcon("SCHOOL", "学习"); val TRAVEL = CategoryIcon("TRAVEL", "出行")
        val FOOD = CategoryIcon("FOOD", "餐饮"); val HEALTH = CategoryIcon("HEALTH", "健康")
        val SHOPPING = CategoryIcon("SHOPPING", "购物"); val MESSAGE = CategoryIcon("MESSAGE", "交流")
        private val legacy = listOf(LIST, INFO, PERSON, FAVORITE, HOME, WORK, SCHOOL, TRAVEL, FOOD, HEALTH, SHOPPING, MESSAGE)
        // Material identifiers cover both original KIGTTS pickers; no legacy UI implementation is reused.
        val commonSymbols = listOf(
            "emoji_emotions", "sentiment_satisfied", "sentiment_very_satisfied", "sentiment_neutral", "sentiment_dissatisfied",
            "record_voice_over", "chat", "forum", "sms", "alternate_email", "emoji_people", "person", "person_add", "groups",
            "accessibility_new", "support_agent", "photo_camera", "visibility", "translate", "work", "school", "home", "restaurant",
            "shopping_bag", "local_hospital", "directions_car", "train", "flight", "location_on", "format_list_bulleted", "schedule",
            "event", "night_shelter", "payments", "sports_esports", "favorite", "thumb_up", "handshake", "celebration", "pets", "info", "warning",
            "music_note", "library_music", "queue_music", "album", "graphic_eq", "equalizer", "volume_up", "campaign", "mic", "radio",
            "piano", "notifications", "alarm", "movie", "theaters", "sports_soccer", "directions_run", "emoji_events", "bolt", "rocket_launch", "mood"
        )
        val entries: List<CategoryIcon> = legacy + commonSymbols.map { CategoryIcon("symbol:$it", it.replace('_', ' '), it) }
        fun valueOf(value: String): CategoryIcon = entries.firstOrNull { it.name == value } ?: run {
            require(value.startsWith("symbol:") && MaterialSymbolCatalog.contains(value.substringAfter(':'))) { "分组图标不存在" }
            val symbol = value.substringAfter(':'); CategoryIcon(value, symbol.replace('_', ' '), symbol)
        }
        fun symbol(name: String): CategoryIcon {
            require(MaterialSymbolCatalog.contains(name)) { "Material Symbols 图标不存在" }
            return valueOf("symbol:$name")
        }
        fun valid(value: String): Boolean = runCatching { valueOf(value) }.isSuccess
        fun defaultFor(category: String) = when (category) { "常用" -> INFO; "生活" -> PERSON; "收藏" -> FAVORITE; else -> LIST }
    }
}
