package com.kigtts.next.state

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CardPreviewRequest(val cardId: String? = null, val version: Long = 0, val openPreview: Boolean = true)

/** Navigation requests are separate from the durable gallery and the main subtitle state. */
class CardWorkspace(private val navigate: (Destination) -> Unit, private val showCards: () -> Unit,
    private val albums: () -> List<PolaroidAlbum> = { emptyList() }, private val cardExists: (String) -> Boolean = { false },
    private val cardEditable: (String) -> Boolean = { true }) {
    private val requested = MutableStateFlow(CardPreviewRequest()); val previewRequest = requested.asStateFlow()
    private val album = MutableStateFlow(DEFAULT_POLAROID_ALBUM_ID); val selectedAlbum = album.asStateFlow()
    fun selectAlbum(id: String) {
        album.value = id.takeIf { selected -> albums().any { it.id == selected } } ?: DEFAULT_POLAROID_ALBUM_ID
    }
    fun previewHandled(version: Long) {
        requested.value.takeIf { it.version == version }?.let { requested.value = it.copy(cardId = null) }
    }
    /** Focus an already visible global preview's album without navigating or opening a second window. */
    fun focusCard(id: String, openPreview: Boolean = false): Boolean {
        if (!cardExists(id)) return false
        albums().firstOrNull { id in it.cardIds }?.let { selectAlbum(it.id) }
        requested.value = CardPreviewRequest(id, requested.value.version + 1, openPreview)
        return true
    }
    fun open(target: StickerUiTarget, id: String?): Boolean {
        when (target) {
            StickerUiTarget.CARDS -> {
                if (id != null) {
                    if (!cardExists(id)) return false
                    albums().firstOrNull { id in it.cardIds }?.let { selectAlbum(it.id) }
                }
                showCards()
                requested.value = if (id != null) CardPreviewRequest(id, requested.value.version + 1)
                    else requested.value.copy(cardId = null)
            }
            StickerUiTarget.LIBRARY -> navigate(Destination.StickerLibrary(id))
            StickerUiTarget.EDIT_CARD -> {
                val card=id ?: return false
                if(!cardExists(card)||!cardEditable(card))return false
                navigate(Destination.CardEditor(card))
            }
            StickerUiTarget.REVIEW -> navigate(Destination.StickerReview(id ?: return false))
        }
        return true
    }
}

/** Child pages keep their editor draft alive, so plugin writes must still respect it. */
internal fun AppState.editingCardId(): String? = (navigationStack + destination)
    .filterIsInstance<Destination.CardEditor>().lastOrNull()?.cardId
