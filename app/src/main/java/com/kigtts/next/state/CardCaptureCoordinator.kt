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
data class CardPrintRequest(val requestId: String, val card: PolaroidCard)

/** Captures are delivered into a retained editor draft or create a new design exactly once. */
class CardCaptureCoordinator(private val cards: CardRepository, private val stickers: StickerRepository,
    private val scope: CoroutineScope, private val state: () -> AppState,
    private val navigate: (Destination) -> Unit, private val back: () -> Unit, private val message: (String) -> Unit,
    private val selectedAlbum: () -> String = { CardRepository.DEFAULT_ALBUM },
    private val onPrintedCard: (String) -> Unit = {}) {
    private val photo = MutableStateFlow<CapturedCardPhoto?>(null); val result = photo.asStateFlow()
    private val printing = MutableStateFlow<CardPrintRequest?>(null); val printRequest = printing.asStateFlow()
    private var pending = false
    private var albumRequest: Pair<Destination.CardCamera, String>? = null
    fun open(cardId: String? = null) {
        if (printing.value != null) { message("照片正在打印，请稍候"); return }
        printing.value = null
        val destination = Destination.CardCamera(UUID.randomUUID().toString(), cardId)
        albumRequest = destination to selectedAlbum()
        navigate(destination)
    }
    /** Navigation owns the scene lifecycle; leaving it never deletes an already saved photo. */
    fun onDestinationChanged(destination: Destination) {
        val request = printing.value ?: return
        val retained = state().navigationStack.any { it is Destination.CardCamera &&
            it.requestId == request.requestId && it.cardId == null }
        if (!retained && (destination !is Destination.CardCamera || destination.requestId != request.requestId || destination.cardId != null))
            printing.value = null
    }
    fun finishPrint(requestId: String): Boolean {
        val request = printing.value?.takeIf { it.requestId == requestId } ?: return false
        val destination = state().destination
        if (destination !is Destination.CardCamera || destination.requestId != requestId || destination.cardId != null) {
            onDestinationChanged(destination)
            return false
        }
        printing.value = null
        back()
        onPrintedCard(request.card.id)
        return true
    }
    fun captured(destination: Destination.CardCamera, original: File, display: File, format: PolaroidFormat,
        print: Boolean = false) {
        if (pending) return
        if (printing.value?.requestId == destination.requestId) { original.delete(); if (display != original) display.delete(); return }
        if (state().destination != destination) { original.delete(); if (display != original) display.delete(); return }
        if (!scope.isActive) { original.delete(); if (display != original) display.delete(); return }
        val desiredAlbum = albumRequest?.takeIf { it.first == destination }?.second ?: selectedAlbum()
        pending = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val source = stickers.importResource(original)
                    val shown = if (display.canonicalFile == original.canonicalFile) source else stickers.importResource(display)
                    source to shown
                }
                val frameStyleId = "instax.${format.name.lowercase()}.classic"
                if (state().destination != destination) return@launch
                if (destination.cardId == null) {
                    val card = withContext(Dispatchers.IO) {
                        synchronized(stickers) {
                            val targetAlbum = desiredAlbum.takeIf { id -> cards.albums.value.any { it.id == id } } ?: CardRepository.DEFAULT_ALBUM
                            cards.create(PolaroidCard(title = "拍立得", imageId = imported.second.id,
                                originalImageId = imported.first.id, frameStyleId = frameStyleId), targetAlbum)
                        }
                    }
                    if (state().destination == destination && print) {
                        printing.value = CardPrintRequest(destination.requestId, card)
                        return@launch
                    }
                } else photo.value = CapturedCardPhoto(destination.cardId, imported.second.id, imported.first.id,
                    frameStyleId, (photo.value?.version ?: 0) + 1)
                if (state().destination == destination) back()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (printing.value?.requestId == destination.requestId) printing.value = null
                message("照片未能保存，请重试"); if (state().destination == destination) back()
            }
            finally {
                original.delete(); if (display != original) display.delete(); pending = false
                if (albumRequest?.first == destination) albumRequest = null
            }
        }
    }
}
