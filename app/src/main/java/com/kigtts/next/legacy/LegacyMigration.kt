package com.kigtts.next.legacy

import android.content.Context
import android.net.Uri
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Stable content identities make retries safe after a partial import. */
internal class LegacyMigration(private val library: QuickTextLibrary, private val media: MediaRepository,
    context: Context) {
    private val contacts = LegacyContactCards(context)
    suspend fun apply(content: LegacyContent): Pair<Int, Int> = withContext(Dispatchers.Main.immediate) {
        val expected = library.revision.value
        val pendingGroups = content.groups.filter { group -> group.items.any { item -> library.phrases.value.none { it.id == item.id } } }
        require(library.categories.value.size + pendingGroups.count { it.id !in library.categoryIds.value.values } <= 128) { "快捷文本分组过多，请分批导入" }
        val groups = library.categories.value.toMutableList()
        val icons = library.categoryIcons.value.toMutableMap()
        val ids = library.categoryIds.value.toMutableMap()
        val additions = mutableListOf<QuickPhrase>()
        val createdMedia = mutableListOf<String>()
        try {
            withContext(Dispatchers.IO) {
                pendingGroups.forEach { group ->
                    val existing = ids.entries.firstOrNull { it.value == group.id }?.key
                    val name = existing ?: run {
                        var candidate = group.title; var suffix = 2
                        while (candidate in groups) candidate = group.title.take(64) + " (${suffix++})"
                        groups += candidate; ids[candidate] = group.id
                        icons[candidate] = runCatching { CategoryIcon.symbol(group.icon) }.getOrDefault(CategoryIcon.LIST)
                        candidate
                    }
                    group.items.filter { item -> library.phrases.value.none { it.id == item.id } }.forEach { item ->
                        val audioId = item.audio?.let { file -> media.import(Uri.fromFile(file), audioMime(file)).id.also { createdMedia += it } }
                        additions += QuickPhrase(item.text, name, item.id, mediaId = audioId,
                            type = if (audioId == null) QuickCardType.TEXT else QuickCardType.AUDIO,
                            colorMode = if (item.color == null) CardColorMode.DEFAULT else CardColorMode.CUSTOM,
                            color = item.color ?: 0xff00696eL, metadata = item.metadata)
                    }
                }
            }
            check(library.revision.value == expected) { "快捷文本已更新，请重新导入" }
            val restoredCards = withContext(Dispatchers.IO) { contacts.restore(content.cards) }
            if (additions.isNotEmpty()) library.commit(library.phrases.value + additions, groups, icons, ids, expected,
                "legacy-import" to System.currentTimeMillis().toString())
            additions.size to restoredCards
        } catch (error: Exception) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                createdMedia.forEach { media.remove(it) }
            }
            throw error
        }
    }
    private fun audioMime(file: File) = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
}
