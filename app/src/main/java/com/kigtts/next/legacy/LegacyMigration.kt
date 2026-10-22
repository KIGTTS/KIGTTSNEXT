package com.kigtts.next.legacy

import android.graphics.Bitmap
import android.net.Uri
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Stable content identities make a retry safe even after the photo transaction completed. */
internal class LegacyMigration(private val library: QuickTextLibrary, private val media: MediaRepository,
    private val cards: CardRepository) {
    suspend fun apply(content: LegacyContent): Pair<Int, Int> = withContext(Dispatchers.Main.immediate) {
        val expected = library.revision.value
        val pendingGroups = content.groups.filter { group -> group.items.any { item -> library.phrases.value.none { it.id == item.id } } }
        val pendingCards = content.cards.filter { old -> cards.cards.value.none { it.id == old.id } }
        require(cards.cards.value.size + pendingCards.size <= 256) { "相册空间不足，请分批导入（最多 256 张）" }
        require(library.categories.value.size + pendingGroups.count { it.id !in library.categoryIds.value.values } <= 128) { "快捷文本分组过多，请分批导入" }
        val groups = library.categories.value.toMutableList()
        val icons = library.categoryIcons.value.toMutableMap()
        val ids = library.categoryIds.value.toMutableMap()
        val additions = mutableListOf<QuickPhrase>()
        val createdMedia = mutableListOf<String>()
        val createdImages = mutableListOf<String>()
        var photosCommitted = false
        try {
            val designs = withContext(Dispatchers.IO) {
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
                pendingCards.map { old ->
                    val photo = (old.image?.let { cards.stickers.importImage(Uri.fromFile(it)) }
                        ?: solidImage(old.color)).also { createdImages += it.id }
                    val format = if (old.landscape) PolaroidFormat.WIDE else PolaroidFormat.MINI
                    val layers = if (old.qr.isBlank()) emptyList() else {
                        val qr = cards.stickers.createQrCode(old.qr).also { createdImages += it.id }
                        // Photo window is above the note on the complete paper.
                        listOf(CardLayer(id = legacyId("qr/${old.id}"), kind = CardLayerKind.IMAGE,
                            imageId = qr.id, text = old.qr, x = .5, y = if (old.landscape) .43 else .44, width = .55))
                    }
                    PolaroidCard(old.id, old.title, old.note, photo.id, layers,
                        frameStyleId = "instax.${format.name.lowercase()}.classic", originalImageId = photo.id)
                }
            }
            check(library.revision.value == expected) { "快捷文本已更新，请重新导入" }
            withContext(Dispatchers.IO) {
                if (designs.isNotEmpty()) {
                    val edits = mutableListOf<StickerEdit>()
                    if (cards.albums.value.none { it.id == ALBUM }) edits += StickerEdit.PutAlbum(ALBUM, "旧版 KIGTTS 名片")
                    edits += designs.map { StickerEdit.PutCard(it) }
                    edits += StickerEdit.MoveAlbumCards(ALBUM, designs.map { it.id })
                    synchronized(cards.stickers) {
                        val before = cards.stickers.snapshot.value
                        var next = before
                        edits.chunked(100).forEach { next = StickerEdits.apply(next, it) }
                        cards.stickers.replace(next, before.revision)
                    }
                    photosCommitted = true
                }
            }
            if (additions.isNotEmpty()) library.commit(library.phrases.value + additions, groups, icons, ids, expected,
                "legacy-import" to System.currentTimeMillis().toString())
            additions.size to pendingCards.size
        } catch (error: Exception) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                createdMedia.forEach { media.remove(it) }
                if (!photosCommitted && createdImages.isNotEmpty()) cards.stickers.apply(listOf(StickerEdit.RemoveImages(createdImages)))
            }
            throw error
        }
    }
    private fun solidImage(color: Long): StickerImage {
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        image.eraseColor(color.toInt())
        val file = File(cards.stickers.context.cacheDir, "legacy-${java.util.UUID.randomUUID()}.png")
        try { file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return cards.stickers.importImage(Uri.fromFile(file))
        } finally { image.recycle(); file.delete() }
    }
    private fun audioMime(file: File) = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
    companion object { const val ALBUM = "original-kigtts-cards" }
}
