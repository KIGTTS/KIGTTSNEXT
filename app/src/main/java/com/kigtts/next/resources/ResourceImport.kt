package com.kigtts.next.resources

import android.net.Uri
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal suspend fun importResource(model: AppViewModel, archive: ResourceArchive) {
    when (archive.manifest.resourceType) {
        ResourceType.FONT -> withContext(Dispatchers.IO) {
            val context = model.getApplication<android.app.Application>()
            val key = com.kigtts.next.theme.AppearanceAssets(context).importFont(Uri.fromFile(archive.file(requireNotNull(archive.content.payload))))
            com.kigtts.next.theme.InstalledFonts(context).remember(key, archive.manifest.name)
        }
        ResourceType.QUICK_TEXT -> {
            val file = archive.file(requireNotNull(archive.content.payload))
            val reply = model.quickTextBackup.prepareImport(model.getApplication<android.app.Application>().contentResolver, Uri.fromFile(file))
            when (reply) {
                is QuickTextReply.Prepared -> {
                    val result = model.quickTextPlugins.approve(reply.value.token)
                    require(result is QuickTextReply.Applied) { (result as? QuickTextReply.Failure)?.message ?: "快捷文本未导入" }
                }
                is QuickTextReply.Failure -> error(reply.message)
                else -> error("无法读取快捷文本包")
            }
        }
        ResourceType.CARD_FRAME -> withContext(Dispatchers.IO) { model.stickers.identityFrames.install(archive) }
        ResourceType.FRAME -> withContext(Dispatchers.IO) {
            requireNotNull(archive.cameraWatermark) { "旧版边框不再支持，请获取相机边框水印" }
            com.kigtts.next.cards.CameraWatermarkLibrary(model.getApplication())
                .install(archive.manifest.id, archive.file(requireNotNull(archive.content.payload)))
        }
        ResourceType.VOICE -> model.externalFiles.importVoice(archive.file(requireNotNull(archive.content.payload)))
        ResourceType.FILTER -> withContext(Dispatchers.IO) {
            val library = com.kigtts.next.ui.cards.camera.CameraLutLibrary(model.getApplication())
            // Parse every LUT before changing the library.
            archive.content.filters.forEach { archive.file(it.path).reader().use { reader -> com.kigtts.next.media.color.CubeLut.read(reader) } }
            archive.content.filters.forEach { library.import(Uri.fromFile(archive.file(it.path))) }
        }
        else -> withContext(Dispatchers.IO) { importDesigns(model, archive) }
    }
}

/** A single batch replaces imported identities, preserving all existing albums and designs. */
private fun importDesigns(model: AppViewModel, archive: ResourceArchive) {
    val created = mutableListOf<String>()
    try {
        val imageIds = archive.content.imageFiles.mapValues { (_, path) -> model.stickers.importResource(archive.file(path)).id.also(created::add) }
        fun image(id: String?) = id?.let { requireNotNull(imageIds[it]) { "图片引用不存在" } }
        val prefix = "resource." + UUID.randomUUID().toString().replace("-", "") + "."
        val frames = archive.content.frames.associate { it.id to (prefix + it.id.takeLast(40)) }
        val edits = mutableListOf<StickerEdit>()
        if (archive.generators.items.isNotEmpty()) {
            val library = UUID.randomUUID().toString()
            edits += StickerEdit.PutLibrary(StickerLibrary(library, archive.manifest.name))
            com.kigtts.next.cards.generators.GeneratedStickers(model.stickers).install(archive).forEach { (name, imageId) ->
                created += imageId
                edits += StickerEdit.PutItem(StickerItem(UUID.randomUUID().toString(), library, name, imageId))
            }
        }
        archive.content.frames.forEach { style -> edits += StickerEdit.PutFrameStyle(style.copy(id = requireNotNull(frames[style.id]),
            overlayImageId = image(style.overlayImageId), owner = null)) }
        archive.content.snapshot?.let { snapshot ->
            require(snapshot.cards.size <= 1000 && snapshot.items.size <= 2048 && snapshot.libraries.size <= 128)
            val libraryIds = snapshot.libraries.associate { it.id to UUID.randomUUID().toString() }
            snapshot.libraries.forEach { edits += StickerEdit.PutLibrary(it.copy(id = requireNotNull(libraryIds[it.id]))) }
            snapshot.items.forEach { edits += StickerEdit.PutItem(it.copy(id = UUID.randomUUID().toString(),
                libraryId = requireNotNull(libraryIds[it.libraryId]), imageId = requireNotNull(image(it.imageId)))) }
            val albumId = UUID.randomUUID().toString()
            val cardIds = mutableListOf<String>()
            if (snapshot.cards.isNotEmpty()) edits += StickerEdit.PutAlbum(albumId, archive.manifest.name)
            snapshot.cards.forEach { card ->
                val id = UUID.randomUUID().toString(); cardIds += id
                edits += StickerEdit.PutCard(card.copy(id = id, imageId = image(card.imageId), originalImageId = image(card.originalImageId),
                    photoStyle = card.photoStyle?.let { it.copy(lutImageId = image(it.lutImageId)) },
                    frameStyleId = frames[card.frameStyleId] ?: card.frameStyleId,
                    layers = card.layers.map { it.copy(id = UUID.randomUUID().toString(), imageId = image(it.imageId)) }))
            }
            if (cardIds.isNotEmpty()) edits += StickerEdit.MoveAlbumCards(albumId, cardIds)
        }
        model.stickers.apply(edits)
    } catch (failure: Exception) {
        if (created.isNotEmpty()) runCatching { model.stickers.apply(listOf(StickerEdit.RemoveImages(created))) }
        throw failure
    }
}
