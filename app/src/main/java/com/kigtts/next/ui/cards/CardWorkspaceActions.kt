package com.kigtts.next.ui.cards

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

/** Disk imports and rendering stay off the UI thread; each user operation publishes once. */
internal class CardWorkspaceActions(private val context: Context, private val cards: CardRepository,
    private val stickers: StickerRepository, private val scope: CoroutineScope,
    private val openEditor: (String) -> Unit, private val message: (String) -> Unit,
    private val onQuickImage: (String, String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO) {
    private var working by mutableStateOf(false)
    private var quickClosing by mutableStateOf(false)
    val busy get() = working || quickClosing
    var quickCard by mutableStateOf<PolaroidCard?>(null); private set
    fun <T> work(operation: () -> T, complete: (T) -> Unit = {}) {
        startWork({ operation() }, complete, {})
    }
    private fun <T> startWork(operation: suspend () -> T, complete: (T) -> Unit, finish: () -> Unit) {
        if (busy || !scope.isActive) { runCatching(finish); return }
        working = true
        // Install the finally block before dispatch; a queued, cancelled launch may never enter it.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { val result = withContext(io) { operation() }; complete(result) }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { message(failure.message ?: "操作未完成，请重试") }
            finally { runCatching(finish); working = false }
        }
    }
    fun importPhoto(selection: CardPhotoSelection, library: String? = null, asSticker: Boolean = false,
        albumId: String = CardRepository.DEFAULT_ALBUM) {
        if (busy) { selection.release(); message("正在处理图片，请稍后重试"); return }
        startWork({
            val image = if (asSticker) stickers.importImage(selection.uri) else stickers.importPhoto(selection.uri)
            if (!asSticker) {
                val facts = PolaroidPhotoFacts.enrichCapture(context, PolaroidPhotoFacts.read(stickers.imageFile(image.id), selection.fromCamera), cards.designs.requiredFields())
                cards.createPhoto(PolaroidCard(title = "", imageId = image.id), facts, albumId).id
            }
            else {
                val target = stickers.libraries.value.firstOrNull { it.id == library } ?: stickers.libraries.value.first()
                stickers.apply(listOf(StickerEdit.PutItem(StickerItem(libraryId = target.id,
                    name = "贴纸 " + (stickers.items.value.count { it.libraryId == target.id } + 1), imageId = image.id))))
                null
            }
        }, { id -> if (id != null) openEditor(id) else message("已添加到贴纸库") }, selection.release)
    }
    fun cardAction(card: PolaroidCard, action: CardMenuAction) = when (action) {
        CardMenuAction.SAVE -> startWork({ cards.systemAlbum.save(card.id) }, { message("已保存到系统相册") }, {})
        CardMenuAction.COPY -> work({ cards.copy(card) }) { message("已复制拍立得") }
        CardMenuAction.QUICK -> requestQuick(card)
        CardMenuAction.DELETE -> work({ cards.remove(card.id) }) { message("已删除拍立得") }
    }
    fun share(card: PolaroidCard) = startWork({
        val source = exportPolaroidMedia(stickers, card)
        try { preparePolaroidShare(context, source) } finally { releaseMotionExport(source) }
    }, { file ->
        try { sharePolaroid(context, file) } catch (_: Exception) { message("没有可用的分享应用") }
    }, {})
    private fun requestQuick(card: PolaroidCard) { if (!busy) quickCard = card }
    fun cancelQuick() { if (!busy) quickCard = null }
    fun publishQuick(includeFrame: Boolean, includeStickers: Boolean, complete: (() -> Unit) -> Unit) {
        val card = quickCard ?: return
        startWork({
            val current = cards.cards.value.firstOrNull { it.id == card.id } ?: error("这张拍立得已移除")
            val file = exportPolaroidMedia(stickers, current, includeFrame, includeStickers)
            try { stickers.media.import(Uri.fromFile(file), if (file.extension.equals("png", true)) "image/png" else "image/jpeg") }
            finally { releaseMotionExport(file) }
        }, { media ->
            quickClosing = true
            complete {
                if (quickClosing && quickCard?.id == card.id) {
                    quickCard = null; quickClosing = false
                    onQuickImage(media.id, card.description.ifBlank { card.title })
                }
            }
        }, {})
    }
    private fun releaseMotionExport(file: java.io.File) {
        if (file.name.endsWith("MP.jpg", true) && file.canonicalFile.parentFile == java.io.File(stickers.root, "exports").canonicalFile)
            file.delete()
    }
    fun quickSticker(item: StickerItem) = work({ stickers.publishQuickMedia(item.imageId) }) { onQuickImage(it.id, item.name) }
    fun stickerCard(item: StickerItem) = work({ cards.createPhoto(PolaroidCard(title = "", imageId = item.imageId), PolaroidPhotoFacts.read(stickers.imageFile(item.imageId), false)) }) { openEditor(it.id) }
    fun edit(edits: List<StickerEdit>) = work({ stickers.apply(edits) }) { message("已保存") }
}
