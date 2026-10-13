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
    private val onQuickImage: (String, String) -> Unit) {
    var busy by mutableStateOf(false); private set
    fun <T> work(operation: () -> T, complete: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try { val result = withContext(Dispatchers.IO) { operation() }; complete(result) }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { message(failure.message ?: "操作未完成，请重试") }
            finally { busy = false }
        }
    }
    fun newCard() = work({ cards.create(PolaroidCard(title = "拍立得")) }) { openEditor(it.id) }
    fun importPhoto(selection: CardPhotoSelection, library: String? = null, asSticker: Boolean = false) {
        if (busy) { selection.release(); message("正在处理图片，请稍后重试"); return }
        work({
        try {
            val image = stickers.importImage(selection.uri)
            if (!asSticker) cards.create(PolaroidCard(title = "拍立得", imageId = image.id)).id
            else {
                val target = stickers.libraries.value.firstOrNull { it.id == library } ?: stickers.libraries.value.first()
                stickers.apply(listOf(StickerEdit.PutItem(StickerItem(libraryId = target.id,
                    name = "贴纸 " + (stickers.items.value.count { it.libraryId == target.id } + 1), imageId = image.id))))
                null
            }
        } finally { selection.release() }
        }) { id -> if (id != null) openEditor(id) else message("已添加到贴纸库") }
    }
    fun cardAction(card: PolaroidCard, action: CardMenuAction) = when (action) {
        CardMenuAction.COPY -> work({ cards.create(card.copy(id = UUID.randomUUID().toString(), title = (card.title + " 副本").take(128))) }) { message("已复制拍立得") }
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
