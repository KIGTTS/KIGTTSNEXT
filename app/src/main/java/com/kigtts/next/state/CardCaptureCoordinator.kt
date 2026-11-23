package com.kigtts.next.state

import com.kigtts.next.i18n.uiCopy

import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import com.kigtts.next.ui.cards.camera.CameraMotionClip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class CapturedCardPhoto(val cardId: String, val imageId: String, val originalImageId: String,
    val frameStyleId: String, val version: Long, val photoX: Double = .5, val photoY: Double = .5, val photoZoom: Double = 1.0,
    val photoStyle: PolaroidPhotoStyle? = null)
data class CardPrintRequest(val requestId: String, val card: PolaroidCard, val cards: List<PolaroidCard> = listOf(card))
data class CardFrameRequest(val requestId: String, val original: File, val display: File, val motion: CameraMotionClip? = null,
    val fromCamera: Boolean = false)

/** Captures are delivered into a retained editor draft or create a new design exactly once. */
class CardCaptureCoordinator(private val cards: CardRepository, private val stickers: StickerRepository,
    private val scope: CoroutineScope, private val state: () -> AppState,
    private val navigate: (Destination) -> Unit, private val back: () -> Unit, private val message: (String) -> Unit,
    private val selectedAlbum: () -> String = { CardRepository.DEFAULT_ALBUM },
    private val onPrintedCard: (String) -> Unit = {}, private val onPreviewedCard: (String) -> Unit = {},
    private val useSystemCamera: () -> Boolean = { false },
    private val saveMotion: ((String, CameraMotionClip) -> Unit)? = null) {
    private val photo = MutableStateFlow<CapturedCardPhoto?>(null); val result = photo.asStateFlow()
    private val framing = MutableStateFlow<CardFrameRequest?>(null); val frameRequest = framing.asStateFlow()
    private val printing = MutableStateFlow<CardPrintRequest?>(null); val printRequest = printing.asStateFlow()
    private val handoff = MutableStateFlow<CardPrintPreviewHandoff?>(null); val printPreviewHandoff = handoff.asStateFlow()
    private val abortedScenes = mutableSetOf<String>()
    private val pendingState = MutableStateFlow(false); val processing = pendingState.asStateFlow()
    private var pending: Boolean
        get() = pendingState.value
        set(value) { pendingState.value = value }
    private var inFlightFiles = emptySet<String>()
    private var albumRequest: Pair<Destination.CardCamera, String>? = null
    val burst = CardBurstCoordinator(cards, stickers, scope, state, { !pending && printing.value == null },
        { albumRequest?.second ?: selectedAlbum() }, { pending = it }, { printing.value = it }, message)
    fun open(cardId: String? = null, systemCamera: Boolean = useSystemCamera(), fullSystemCamera: Boolean = false) {
        if (pending) { message(stickers.context.uiCopy("camera.photo_saving_wait")); return }
        if (printing.value != null) { message(stickers.context.uiCopy("camera.photo_saving_wait")); return }
        printing.value = null
        val destination = Destination.CardCamera(UUID.randomUUID().toString(), cardId, systemCamera, fullSystemCamera)
        albumRequest = destination to selectedAlbum()
        navigate(destination)
    }
    /** Navigation owns the scene lifecycle; leaving it never deletes an already saved photo. */
    fun onDestinationChanged(destination: Destination) {
        burst.review.value?.takeIf { (destination as? Destination.CardCamera)?.requestId != it.requestId }
            ?.let { burst.cancel(it.requestId) }
        framing.value?.takeIf { (destination as? Destination.CardCamera)?.requestId != it.requestId }?.let { cancelFraming(it.requestId) }
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
    fun prepareFraming(destination: Destination.CardCamera, original: File, display: File, motion: CameraMotionClip? = null, fromCamera: Boolean = false): Boolean {
        if (state().destination != destination || pending || printing.value != null || framing.value != null) return false
        framing.value = CardFrameRequest(destination.requestId, original, display, motion, fromCamera)
        return true
    }
    fun cancelFraming(requestId: String) {
        if (pending) return
        framing.value?.takeIf { it.requestId == requestId }?.let {
            framing.value = null; it.original.delete(); if (it.display != it.original) it.display.delete(); it.motion?.video?.delete()
        }
    }
    fun confirmFraming(destination: Destination.CardCamera, format: PolaroidFormat, x: Double, y: Double, zoom: Double): Boolean {
        val frame = framing.value?.takeIf { it.requestId == destination.requestId } ?: return false
        require(x in 0.0..1.0 && y in 0.0..1.0 && zoom in 1.0..8.0)
        return captured(destination, frame.original, frame.display, format, print = destination.cardId == null, motion = frame.motion, fromCamera = frame.fromCamera, photoX = x, photoY = y, photoZoom = zoom)
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
        print: Boolean = false, motion: CameraMotionClip? = null, fromCamera: Boolean = true, photoX: Double = .5, photoY: Double = .5, photoZoom: Double = 1.0,
        photoStyle: CapturedPhotoStyle? = null): Boolean {
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
                var savedStyle: PolaroidPhotoStyle? = null
                val imported = withContext(Dispatchers.IO) {
                    val source = stickers.importResource(original)
                    val shown = if (display.canonicalFile == original.canonicalFile) source else stickers.importResource(display)
                    savedStyle = photoStyle?.persist(stickers)
                    motion?.let { clip ->
                        try { requireNotNull(saveMotion) { "实况存储尚未就绪" }.invoke(shown.id, clip) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { motionFailed = true }
                    }
                    source to shown
                }
                if (motionFailed) message(stickers.context.uiCopy("camera.motion_clip_save_failed"))
                val frameStyleId = "instax.${format.name.lowercase()}.classic"
                if (state().destination != destination) return@launch
                if (destination.cardId == null) {
                    val facts = PolaroidPhotoFacts.enrichCapture(stickers.context,
                        withContext(Dispatchers.IO) { PolaroidPhotoFacts.read(original, fromCamera = fromCamera) }, cards.designs.requiredFields())
                    val card = withContext(Dispatchers.IO) {
                        synchronized(stickers) {
                            val targetAlbum = desiredAlbum.takeIf { id -> cards.albums.value.any { it.id == id } } ?: CardRepository.DEFAULT_ALBUM
                            cards.createPhoto(PolaroidCard(title = "", imageId = imported.second.id,
                                originalImageId = imported.first.id, frameStyleId = frameStyleId, photoX = photoX, photoY = photoY, photoZoom = photoZoom,
                                photoStyle = savedStyle), facts, targetAlbum)
                        }
                    }
                    if (state().destination == destination && print) {
                        printing.value = CardPrintRequest(destination.requestId, card)
                        return@launch
                    }
                } else photo.value = CapturedCardPhoto(destination.cardId, imported.second.id, imported.first.id,
                    frameStyleId, (photo.value?.version ?: 0) + 1, photoX, photoY, photoZoom, savedStyle)
                if (state().destination == destination) back()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (printing.value?.requestId == destination.requestId) printing.value = null
                message(stickers.context.uiCopy("camera.photo_save_failed")); if (state().destination == destination) back()
            }
            finally {
                if (framing.value?.requestId == destination.requestId) framing.value = null
                discardFiles(); inFlightFiles = emptySet(); pending = false
                if (albumRequest?.first == destination) albumRequest = null
            }
        }
        return true
    }
}
