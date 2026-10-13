package com.kigtts.next.state

import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import com.kigtts.next.ui.cards.camera.CameraMotionClip
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
    private val onPrintedCard: (String) -> Unit = {}, private val onPreviewedCard: (String) -> Unit = {},
    private val saveMotion: ((String, CameraMotionClip) -> Unit)? = null) {
    private val photo = MutableStateFlow<CapturedCardPhoto?>(null); val result = photo.asStateFlow()
    private val printing = MutableStateFlow<CardPrintRequest?>(null); val printRequest = printing.asStateFlow()
    private val handoff = MutableStateFlow<CardPrintPreviewHandoff?>(null); val printPreviewHandoff = handoff.asStateFlow()
    private val abortedScenes = mutableSetOf<String>()
    private val pendingState = MutableStateFlow(false); val processing = pendingState.asStateFlow()
    private var pending: Boolean
        get() = pendingState.value
        set(value) { pendingState.value = value }
    private var inFlightFiles = emptySet<String>()
    private var albumRequest: Pair<Destination.CardCamera, String>? = null
    fun open(cardId: String? = null) {
        if (pending) { message("照片正在保存，请稍候"); return }
        if (printing.value != null) { message("照片正在打印，请稍候"); return }
        printing.value = null
        val destination = Destination.CardCamera(UUID.randomUUID().toString(), cardId)
        albumRequest = destination to selectedAlbum()
        navigate(destination)
    }
    /** Navigation owns the scene lifecycle; leaving it never deletes an already saved photo. */
    fun onDestinationChanged(destination: Destination) {
        abortedScenes.removeAll { !cameraRetained(it, destination) }
        val aborted = (destination as? Destination.CardCamera)?.requestId
        if (aborted != null && cameraForeground(aborted) && abortedScenes.remove(aborted)) {
            back()
            return
        }
        handoff.value?.takeIf { !it.shown }?.let { preview ->
            if (!cameraRetained(preview.requestId, destination)) handoff.value = null
        }
        val request = printing.value ?: return
        if (!cameraRetained(request.requestId, destination)) printing.value = null
    }
    private fun cameraRetained(requestId: String, destination: Destination): Boolean =
        destination is Destination.CardCamera && destination.requestId == requestId && destination.cardId == null ||
            state().navigationStack.any { it is Destination.CardCamera && it.requestId == requestId && it.cardId == null }
    private fun cameraForeground(requestId: String): Boolean = (state().destination as? Destination.CardCamera)?.let {
        it.requestId == requestId && it.cardId == null
    } == true
    fun preparePrintPreview(requestId: String, origin: CardPrintOrigin): Boolean {
        val request = printing.value?.takeIf { it.requestId == requestId } ?: return false
        if (!origin.usable || !cameraForeground(requestId)) return false
        val existing = handoff.value
        if (existing != null) return existing.requestId == requestId && !existing.shown
        handoff.value = CardPrintPreviewHandoff(requestId, request.card, origin)
        return true
    }
    /** Only the preview's real first paper draw acknowledges delivery and navigates the background. */
    fun previewShown(requestId: String): Boolean {
        val preview = handoff.value?.takeIf { it.requestId == requestId && !it.shown } ?: return false
        if (printing.value?.requestId != requestId || !cameraForeground(requestId)) return false
        handoff.value = preview.copy(shown = true)
        printing.value = null
        back()
        onPreviewedCard(preview.card.id)
        return true
    }
    fun dismissPrintPreview(requestId: String) {
        if (handoff.value?.requestId == requestId) handoff.value = null
    }
    /** Withdraw a missing paper without deleting resources or popping a covering child page. */
    fun abortPrintPreview(requestId: String): Boolean {
        val pendingPrint = printing.value?.takeIf { it.requestId == requestId }
        val preview = handoff.value?.takeIf { it.requestId == requestId }
        if (pendingPrint == null && preview == null) return false
        if (preview != null) handoff.value = null
        if (pendingPrint != null) {
            printing.value = null
            if (cameraForeground(requestId)) back()
            else if (cameraRetained(requestId, state().destination)) abortedScenes += requestId
        }
        return true
    }
    fun finishPrint(requestId: String): Boolean {
        val request = printing.value?.takeIf { it.requestId == requestId } ?: return false
        val destination = state().destination
        if (destination !is Destination.CardCamera || destination.requestId != requestId || destination.cardId != null) {
            onDestinationChanged(destination)
            return false
        }
        printing.value = null
        if (handoff.value?.requestId == requestId) handoff.value = null
        back()
        onPrintedCard(request.card.id)
        return true
    }
    fun captured(destination: Destination.CardCamera, original: File, display: File, format: PolaroidFormat,
        print: Boolean = false, motion: CameraMotionClip? = null): Boolean {
        fun discardFiles() { original.delete(); if (display != original) display.delete(); motion?.video?.delete() }
        if (pending) {
            listOfNotNull(original, display, motion?.video).filterNot { it.canonicalPath in inFlightFiles }.forEach { it.delete() }
            return false
        }
        if (printing.value?.requestId == destination.requestId) { discardFiles(); return false }
        if (state().destination != destination) { discardFiles(); return false }
        if (!scope.isActive) { discardFiles(); return false }
        val desiredAlbum = albumRequest?.takeIf { it.first == destination }?.second ?: selectedAlbum()
        pending = true
        inFlightFiles = listOfNotNull(original, display, motion?.video).map { it.canonicalPath }.toSet()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                var motionFailed = false
                val imported = withContext(Dispatchers.IO) {
                    val source = stickers.importResource(original)
                    val shown = if (display.canonicalFile == original.canonicalFile) source else stickers.importResource(display)
                    motion?.let { clip ->
                        try { requireNotNull(saveMotion) { "实况存储尚未就绪" }.invoke(shown.id, clip) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { motionFailed = true }
                    }
                    source to shown
                }
                if (motionFailed) message("实况短片未能保存，本次保留照片")
                val frameStyleId = "instax.${format.name.lowercase()}.classic"
                if (state().destination != destination) return@launch
                if (destination.cardId == null) {
                    val card = withContext(Dispatchers.IO) {
                        synchronized(stickers) {
                            val targetAlbum = desiredAlbum.takeIf { id -> cards.albums.value.any { it.id == id } } ?: CardRepository.DEFAULT_ALBUM
                            cards.create(PolaroidCard(title = "", imageId = imported.second.id,
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
                discardFiles(); inFlightFiles = emptySet(); pending = false
                if (albumRequest?.first == destination) albumRequest = null
            }
        }
        return true
    }
}
