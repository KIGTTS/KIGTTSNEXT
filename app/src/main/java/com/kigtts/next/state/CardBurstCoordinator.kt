package com.kigtts.next.state

import com.kigtts.next.cards.*
import com.kigtts.next.ui.cards.camera.PolaroidCameraCapture
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CardBurstReview(val requestId: String, val photos: List<PolaroidCameraCapture>)

/** Owns unselected originals across recomposition/rotation, then imports selected designs exactly once. */
class CardBurstCoordinator internal constructor(private val cards: CardRepository, private val stickers: StickerRepository,
    private val scope: CoroutineScope, private val state: () -> AppState, private val canAccept: () -> Boolean,
    private val selectedAlbum: () -> String, private val busy: (Boolean) -> Unit,
    private val print: (CardPrintRequest) -> Unit, private val message: (String) -> Unit) {
    private val reviewing = MutableStateFlow<CardBurstReview?>(null); val review = reviewing.asStateFlow()
    private val progress = MutableStateFlow(0); val savedCount = progress.asStateFlow()
    private val saved = mutableMapOf<String, PolaroidCard>()
    private var saving = false
    private var album = CardRepository.DEFAULT_ALBUM
    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            val owned = reviewing.value; reviewing.value = null
            owned?.photos?.forEach { it.discard() }
        }
    }

    fun receive(destination: Destination.CardCamera, photos: List<PolaroidCameraCapture>): Boolean {
        if (photos.isEmpty()) return false
        if (!canAccept() || state().destination != destination || destination.cardId != null || reviewing.value != null || !scope.isActive) {
            val owned = reviewing.value?.photos.orEmpty().map { it.original.path }.toSet()
            photos.filterNot { it.original.path in owned }.forEach { it.discard() }; return false
        }
        album = selectedAlbum(); saved.clear(); progress.value = 0
        reviewing.value = CardBurstReview(destination.requestId, photos.distinctBy { it.original.path })
        return true
    }

    fun cancel(requestId: String): Boolean {
        val review = reviewing.value?.takeIf { it.requestId == requestId } ?: return false
        if (saving) return false
        reviewing.value = null; saved.clear(); review.photos.forEach { it.discard() }
        return true
    }

    fun confirm(destination: Destination.CardCamera, selected: Set<String>): Boolean {
        val review = reviewing.value?.takeIf { it.requestId == destination.requestId } ?: return false
        val photos = review.photos.filter { it.original.path in selected }
        if (saving || !canAccept() || state().destination != destination || photos.isEmpty() || !scope.isActive) return false
        saving = true; busy(true); progress.value = 0
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                var snapshot: FrozenPhotoFacts? = null
                val imported = photos.map { shot ->
                    val existing = saved[shot.original.path]
                    val card = existing ?: run {
                        val facts = withContext(Dispatchers.IO) { PolaroidPhotoFacts.read(shot.original, true, shot.capturedAt) }
                        val frozen = snapshot?.let { facts.copy(latitude = it.latitude, longitude = it.longitude, location = it.location, weather = it.weather) }
                            ?: PolaroidPhotoFacts.enrichCapture(stickers.context, facts, cards.designs.requiredFields()).also { snapshot = it }
                        withContext(Dispatchers.IO) {
                            val source = stickers.importResource(shot.original)
                            val shown = if (shot.displayFile.canonicalFile == shot.original.canonicalFile) source else stickers.importResource(shot.displayFile)
                            synchronized(stickers) {
                                val target = album.takeIf { id -> cards.albums.value.any { it.id == id } } ?: CardRepository.DEFAULT_ALBUM
                                cards.createPhoto(PolaroidCard(title = "", imageId = shown.id, originalImageId = source.id,
                                    frameStyleId = "instax.${shot.format.name.lowercase()}.classic"), frozen, target)
                            }
                        }.also { saved[shot.original.path] = it }
                    }
                    progress.value++; card
                }
                if (state().destination == destination) print(CardPrintRequest(destination.requestId, imported.last(), imported))
                reviewing.value = null; saved.clear(); review.photos.forEach { it.discard() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("部分照片未能保存，请重试") }
            finally {
                saving = false; busy(false)
                if (state().destination != destination) cancel(destination.requestId)
            }
        }
        return true
    }
}
