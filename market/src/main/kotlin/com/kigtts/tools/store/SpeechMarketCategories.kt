package com.kigtts.tools.store

import com.kigtts.plugin.api.OfficialPluginSource

private val synthesis = CatalogCategory("plugin.speech-synthesis", "语音合成", symbol = "record_voice_over")
private val recognition = CatalogCategory("plugin.speech-recognition", "语音识别", symbol = "mic")

internal fun speechCategoryForPage(id: String): CatalogCategory? = when (id) {
    "speech/synthesis" -> synthesis
    "speech/recognition" -> recognition
    else -> null
}

internal fun speechCategoryForKey(key: String): CatalogCategory? =
    listOf(synthesis, recognition).firstOrNull { categoryKey(it.id) == key || categoryKey(it.name) == key }

/** Older official catalogs put both engine types in Audio. Explicit categories from other sources are preserved. */
internal fun PackageCatalog.withLegacySpeechCategories(): PackageCatalog {
    if (id != OfficialPluginSource.ID) return this
    val needed = linkedSetOf<CatalogCategory>()
    val updated = packages.map { item ->
        if (item.resourceType != null || item.categoryId !in setOf(null, "plugin.audio") ||
            item.categoryId == null && item.category != "音频") return@map item
        val dependencies = item.releases.flatMap { it.dependencies.keys }.toSet()
        val target = listOf(synthesis, recognition).singleOrNull {
            "service.${it.id.substringAfter('.')}" in dependencies
        } ?: return@map item
        needed += target
        item.copy(category = target.name, categoryId = target.id)
    }
    if (needed.isEmpty()) return this
    val hasAudio = updated.any { it.categoryId == "plugin.audio" || it.categoryId == null && it.category == "音频" }
    return copy(packages = updated, categories = categories.filter { hasAudio || it.id != "plugin.audio" } +
        needed.filter { addition -> categories.none { it.id == addition.id } })
}
