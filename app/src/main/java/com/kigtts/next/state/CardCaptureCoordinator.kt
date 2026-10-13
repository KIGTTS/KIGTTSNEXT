package com.kigtts.next.state

import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class CapturedCardPhoto(val cardId: String, val imageId: String, val originalImageId: String,
    val frameStyleId: String, val version: Long)

/** Captures are delivered into a retained editor draft or create a new design exactly once. */
class CardCaptureCoordinator(private val cards: CardRepository, private val stickers: StickerRepository,
    private val scope: CoroutineScope, private val state: () -> AppState,
    private val navigate: (Destination) -> Unit, private val back: () -> Unit, private val message: (String) -> Unit) {
    private val photo = MutableStateFlow<CapturedCardPhoto?>(null); val result = photo.asStateFlow()
    private var pending = false
    fun open(cardId: String? = null) { navigate(Destination.CardCamera(UUID.randomUUID().toString(), cardId)) }
    fun captured(destination: Destination.CardCamera, original: File, display: File, format: PolaroidFormat) {
        if (pending) return
        if (state().destination != destination) { original.delete(); if (display != original) display.delete(); return }
        pending = true
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val source = stickers.importResource(original)
                    val shown = if (display.canonicalFile == original.canonicalFile) source else stickers.importResource(display)
                    source to shown
                }
                val frameStyleId = "instax.${format.name.lowercase()}.classic"
                if (state().destination != destination) return@launch
                if (destination.cardId == null) withContext(Dispatchers.IO) {
                    cards.create(PolaroidCard(title = "拍立得", imageId = imported.second.id,
                        originalImageId = imported.first.id, frameStyleId = frameStyleId))
                } else photo.value = CapturedCardPhoto(destination.cardId, imported.second.id, imported.first.id,
                    frameStyleId, (photo.value?.version ?: 0) + 1)
                if (state().destination == destination) back()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("照片未能保存，请重试"); if (state().destination == destination) back() }
            finally { original.delete(); if (display != original) display.delete(); pending = false }
        }
    }
}
