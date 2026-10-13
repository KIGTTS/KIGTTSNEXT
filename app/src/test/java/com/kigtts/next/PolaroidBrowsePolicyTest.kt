package com.kigtts.next

import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class PolaroidBrowsePolicyTest {
    @Test fun albumBoundariesSkipEmptyAlbumsWithoutLosingOrDuplicatingCards() {
        val first = PolaroidCard(id = "card.first", title = "第一张")
        val second = PolaroidCard(id = "card.second", title = "第二张")
        val unattached = PolaroidCard(id = "card.unattached", title = "迁移中")
        val albums = listOf(PolaroidAlbum("album.one", "一", listOf(first.id)), PolaroidAlbum("album.empty", "空"),
            PolaroidAlbum("album.two", "二", listOf(second.id, first.id, "missing")))
        assertEquals(listOf(first.id, second.id, unattached.id), orderedAlbumCards(albums, listOf(second, unattached, first)).map { it.id })
        assertEquals("album.two", albumContaining(albums, second.id)?.id)
    }
    @Test fun pinchHasStableBandsAndNeverExceedsTheSupportedColumns() {
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.STACK, 2), pinchBrowseScale(PolaroidBrowseMode.STACK, 2, .95f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.GRID, 2), pinchBrowseScale(PolaroidBrowseMode.STACK, 2, .8f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.GRID, 3), pinchBrowseScale(PolaroidBrowseMode.GRID, 2, .8f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.ALBUMS, 4), pinchBrowseScale(PolaroidBrowseMode.GRID, 4, .8f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.ALBUMS, 4), pinchBrowseScale(PolaroidBrowseMode.ALBUMS, 4, .8f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.GRID, 4), pinchBrowseScale(PolaroidBrowseMode.ALBUMS, 4, 1.3f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.GRID, 2), pinchBrowseScale(PolaroidBrowseMode.GRID, 3, 1.3f))
        assertEquals(PolaroidBrowseScale(PolaroidBrowseMode.STACK, 2), pinchBrowseScale(PolaroidBrowseMode.GRID, 2, 1.3f))
    }
}
