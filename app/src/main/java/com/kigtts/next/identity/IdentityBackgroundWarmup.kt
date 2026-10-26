package com.kigtts.next.identity

import com.kigtts.next.cards.StickerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Warm the selected card before settings is entered; profile-only edits do not invalidate its bake. */
internal fun warmIdentityBackground(scope: CoroutineScope, stickers: StickerRepository, identities: IdentityRepository) {
    scope.launch(Dispatchers.Default) {
        combine(identities.items, identities.selectedId, stickers.identityDesigns.items) { cards, selected, designs ->
            val person = cards.firstOrNull { it.id == selected } ?: cards.firstOrNull()
            person?.let { identity -> designs.firstOrNull { it.id == identity.designId }?.let { it to identity } }
        }.distinctUntilChangedBy { pair -> pair?.let { stickers.identityBackgrounds.key(it.first, it.second) } }
            .collectLatest { pair ->
                if (pair != null) try {
                    val height = (stickers.context.resources.displayMetrics.widthPixels * 9f / 16f).toInt()
                    stickers.identityBackgrounds.load(pair.first, pair.second, height)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* An unavailable cache must not prevent using settings. */ }
            }
    }
}
