package com.kigtts.next.identity

import android.content.Context
import com.kigtts.next.cards.CardRepository
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Private card backgrounds. They share image assets, never the photo album or its lifecycle. */
class IdentityDesignStore(context: Context) {
    private val preferences = context.getSharedPreferences("identity-designs", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(runCatching {
        PluginJson.decodeFromString<List<PolaroidCard>>(preferences.getString("designs", "[]")!!)
    }.getOrDefault(emptyList()))
    val items = state.asStateFlow()
    @Synchronized fun migrate(identities: List<IdentityCard>, photos: CardRepository) {
        val ids = identities.map { it.designId }.toSet()
        val legacy = photos.cards.value.filter { it.id in ids && it.frameStyleId == "identity.banner" }
        if (legacy.isNotEmpty()) {
            persist(state.value + legacy.filter { old -> state.value.none { it.id == old.id } })
            // Commit the new owner first; a retry after interruption cannot lose or duplicate a design.
            photos.removeCards(legacy.map { it.id })
        }
        photos.albums.value.firstOrNull { it.id == "personal-cards" && it.cardIds.isEmpty() }?.let { photos.removeAlbum(it.id) }
    }
    @Synchronized fun put(value: PolaroidCard): PolaroidCard {
        require(value.frameStyleId == "identity.banner" && value.layers.size <= 64)
        val next = state.value.filterNot { it.id == value.id } + value
        persist(next); return value
    }
    @Synchronized fun remove(id: String) { persist(state.value.filterNot { it.id == id }) }
    fun get(id: String) = state.value.firstOrNull { it.id == id }
    private fun persist(next: List<PolaroidCard>) {
        check(preferences.edit().putString("designs", PluginJson.encodeToString(next)).commit()) { "背景未能保存，请重试" }
        state.value = next
    }
}
