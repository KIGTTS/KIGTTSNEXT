package com.kigtts.next.ui.cards

import com.kigtts.next.cards.*
import com.kigtts.next.cards.generators.*
import com.kigtts.plugin.api.CardLayer
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun preparePhotoSticker(layer: CardLayer, card: PolaroidCard, cards: CardRepository,
    stickers: StickerRepository): CardLayer {
    val generators = GeneratedStickers(stickers)
    val source = withContext(Dispatchers.IO) { generators.get(layer.imageId) } ?: return layer
    if (!source.templateEntry || source.photoFields.isEmpty()) return layer
    val saved = cards.cards.value.firstOrNull { it.id == card.id }
    val useFrozen = saved != null && (saved.originalImageId ?: saved.imageId) == (card.originalImageId ?: card.imageId)
    val existing = withContext(Dispatchers.IO) {
        if (useFrozen && cards.designs.hasFacts(card.id)) cards.designs.facts(card.id)
        else PolaroidPhotoFacts.read(stickers.imageFile(card.originalImageId ?: card.imageId.orEmpty()), false)
    }
    val fields = source.photoFields.values.mapNotNull { it.captureField }.toSet()
    val facts = PolaroidPhotoFacts.enrich(stickers.context, existing, fields)
    return withContext(Dispatchers.IO) {
        if (useFrozen) cards.designs.freeze(card.id, facts)
        layer.copy(imageId = generators.generate(source, PhotoStickerDefaults.resolve(source, source.photoFields, facts)))
    }
}
