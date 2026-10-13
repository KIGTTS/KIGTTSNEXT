package com.kigtts.next.state

import com.kigtts.plugin.api.StickerUiTarget
/** Navigation requests are separate from the durable gallery and the main subtitle state. */
class CardWorkspace(private val navigate: (Destination) -> Unit, private val showCards: () -> Unit) {
    fun open(target: StickerUiTarget, id: String?): Boolean {
        when (target) {
            StickerUiTarget.CARDS -> showCards()
            StickerUiTarget.LIBRARY -> navigate(Destination.StickerLibrary(id))
            StickerUiTarget.EDIT_CARD -> navigate(Destination.CardEditor(id ?: return false))
            StickerUiTarget.REVIEW -> navigate(Destination.StickerReview(id ?: return false))
        }
        return true
    }
}

/** Child pages keep their editor draft alive, so plugin writes must still respect it. */
internal fun AppState.editingCardId(): String? = (navigationStack + destination)
    .filterIsInstance<Destination.CardEditor>().lastOrNull()?.cardId
