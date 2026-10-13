package com.kigtts.next.ui.cards

import android.content.Context
import androidx.compose.runtime.*
import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.util.UUID

/** Disk imports and rendering stay off the UI thread; each user operation publishes once. */
internal class CardWorkspaceActions(private val context: Context, private val cards: CardRepository,
    private val stickers: StickerRepository, private val scope: CoroutineScope,
    private val openEditor: (String) -> Unit, private val message: (String) -> Unit,
    private val onQuickImage: (String, String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO) {
    var busy by mutableStateOf(false); private set
    fun <T> work(operation: () -> T, complete: (T) -> Unit = {}) {
        startWork(operation, complete, {})
    }
    private fun <T> startWork(operation: () -> T, complete: (T) -> Unit, finish: () -> Unit) {
        if (busy || !scope.isActive) { runCatching(finish); return }
        busy = true
        // Install the finally block before dispatch; a queued, cancelled launch may never enter it.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { val result = withContext(io) { operation() }; complete(result) }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { message(failure.message ?: "操作未完成，请重试") }
            finally { runCatching(finish); busy = false }
        }
    }
    fun newCard(albumId: String = CardRepository.DEFAULT_ALBUM) = work({ cards.create(PolaroidCard(title = "拍立得"), albumId) }) { openEditor(it.id) }
    fun importPhoto(selection: CardPhotoSelection, library: String? = null, asSticker: Boolean = false,
        albumId: String = CardRepository.DEFAULT_ALBUM) {
        if (busy) { selection.release(); message("正在处理图片，请稍后重试"); return }
        startWork({
            val image = stickers.importImage(selection.uri)
            if (!asSticker) cards.create(PolaroidCard(title = "拍立得", imageId = image.id), albumId).id
            else {
                val target = stickers.libraries.value.firstOrNull { it.id == library } ?: stickers.libraries.value.first()
                stickers.apply(listOf(StickerEdit.PutItem(StickerItem(libraryId = target.id,
                    name = "贴纸 " + (stickers.items.value.count { it.libraryId == target.id } + 1), imageId = image.id))))
                null
            }
        }, { id -> if (id != null) openEditor(id) else message("已添加到贴纸库") }, selection.release)
    }
    fun cardAction(card: PolaroidCard, action: CardMenuAction) = when (action) {
        CardMenuAction.COPY -> work({ cards.create(card.copy(id = UUID.randomUUID().toString(), title = (card.title + " 副本").take(128)),
            albumContaining(cards.albums.value, card.id)?.id ?: CardRepository.DEFAULT_ALBUM) }) { message("已复制拍立得") }
        CardMenuAction.QUICK -> work({
            val image = stickers.importResource(stickers.exportCard(card.id))
            stickers.publishQuickMedia(image.id)
        }) { onQuickImage(it.id, card.title) }
        CardMenuAction.DELETE -> work({ cards.remove(card.id) }) { message("已删除拍立得") }
    }
    fun share(card: PolaroidCard) = work({ preparePolaroidShare(context, stickers.exportCard(card.id)) }) { file ->
        try { sharePolaroid(context, file) } catch (_: Exception) { message("没有可用的分享应用") }
    }
    fun quickSticker(item: StickerItem) = work({ stickers.publishQuickMedia(item.imageId) }) { onQuickImage(it.id, item.name) }
    fun stickerCard(item: StickerItem) = work({ cards.create(PolaroidCard(title = item.name, imageId = item.imageId)) }) { openEditor(it.id) }
    fun edit(edits: List<StickerEdit>) = work({ stickers.apply(edits) }) { message("已保存") }
}
