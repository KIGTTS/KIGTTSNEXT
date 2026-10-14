package com.kigtts.next.ui.cards

import com.kigtts.plugin.api.PolaroidAlbum
import com.kigtts.plugin.api.PolaroidCard

internal enum class PolaroidBrowseMode { STACK, GRID, ALBUMS }

/** Flatten album order once: a pager's next card naturally crosses an album boundary. */
internal fun orderedAlbumCards(albums: List<PolaroidAlbum>, cards: List<PolaroidCard>): List<PolaroidCard> {
    val byId = cards.associateBy { it.id }
    val ordered = albums.flatMap { album -> album.cardIds.mapNotNull(byId::get) }.distinctBy { it.id }
    val used = ordered.mapTo(HashSet()) { it.id }
    return ordered + cards.filter { it.id !in used }
}

internal fun albumContaining(albums: List<PolaroidAlbum>, cardId: String?): PolaroidAlbum? =
    albums.firstOrNull { cardId != null && cardId in it.cardIds }

internal fun pinchBrowseMode(current: PolaroidBrowseMode, zoom: Float): PolaroidBrowseMode = when {
    current == PolaroidBrowseMode.STACK && zoom < .84f -> PolaroidBrowseMode.GRID
    current == PolaroidBrowseMode.GRID && zoom > 1.18f -> PolaroidBrowseMode.STACK
    current == PolaroidBrowseMode.ALBUMS && zoom > 1.18f -> PolaroidBrowseMode.GRID
    else -> current
}

internal data class PolaroidBrowseScale(val mode: PolaroidBrowseMode, val columns: Int)

internal fun pinchBrowseScale(current: PolaroidBrowseMode, columns: Int, zoom: Float,
    minimum: Int = 2, maximum: Int = 4): PolaroidBrowseScale = when {
    current == PolaroidBrowseMode.STACK && zoom < .84f -> PolaroidBrowseScale(PolaroidBrowseMode.GRID, columns)
    current == PolaroidBrowseMode.GRID && zoom < .84f && columns >= maximum -> PolaroidBrowseScale(PolaroidBrowseMode.ALBUMS, maximum)
    current == PolaroidBrowseMode.GRID && zoom < .84f -> PolaroidBrowseScale(current, columns + 1)
    current == PolaroidBrowseMode.GRID && zoom > 1.18f && columns > minimum -> PolaroidBrowseScale(current, columns - 1)
    current == PolaroidBrowseMode.GRID && zoom > 1.18f -> PolaroidBrowseScale(PolaroidBrowseMode.STACK, columns)
    current == PolaroidBrowseMode.ALBUMS && zoom > 1.18f -> PolaroidBrowseScale(PolaroidBrowseMode.GRID, maximum)
    else -> PolaroidBrowseScale(current, columns)
}
