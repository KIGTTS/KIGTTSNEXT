package com.kigtts.next

import com.kigtts.next.state.*
import com.kigtts.next.ui.cards.camera.*
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CardBurstCoordinatorTest {
    @Test fun selectedPhotosAreSavedToTheOriginalAlbumExactlyOnceBeforePrintingAndUnselectedFilesAreReleased() = runBlocking {
        val f = CardPrintCoordinatorTest.Fixture(); val album = f.cards.createAlbum("连拍相册"); f.selected = album.id
        val before = f.cards.cards.value.map { it.id }.toSet()
        val photos = List(4) { PolaroidCameraCapture(f.photo(), null, PolaroidFormat.MINI, PhotoFilter.ORIGINAL) }
        try {
            f.coordinator.open(); val destination = f.state.value.destination as Destination.CardCamera
            assertTrue(f.coordinator.burst.receive(destination, photos))
            f.selected = f.cards.createAlbum("后来选择").id
            val selected = setOf(photos.first().original.path, photos.last().original.path)
            assertTrue(f.coordinator.burst.confirm(destination, selected))
            assertFalse(f.coordinator.burst.confirm(destination, selected))
            withTimeout(10_000) { while (f.coordinator.processing.value || f.coordinator.printRequest.value == null) delay(10) }
            val request = requireNotNull(f.coordinator.printRequest.value)
            assertEquals(2, request.cards.size); assertEquals(request.cards.last(), request.card)
            assertEquals(2, f.cards.cards.value.count { it.id !in before })
            assertEquals(request.cards.map { it.id }.toSet(), f.cards.cardsInAlbum(album.id).map { it.id }.toSet())
            request.cards.forEach(f::assertSaved); assertTrue(photos.none { it.original.exists() })
            assertNull(f.coordinator.burst.review.value); assertEquals(0, f.backs)
            assertTrue(f.coordinator.finishPrint(request.requestId)); assertFalse(f.coordinator.finishPrint(request.requestId))
            assertEquals(1, f.backs); assertEquals(listOf(request.card.id), f.printed)
        } finally { f.scope.cancel(); photos.forEach { it.discard() } }
    }

    @Test fun cancellingReviewReleasesOnlyItsOwnFilesAndEmptySelectionCannotStartSaving() {
        val f = CardPrintCoordinatorTest.Fixture()
        val photo = PolaroidCameraCapture(f.photo(), null, PolaroidFormat.MINI, PhotoFilter.ORIGINAL)
        try {
            f.coordinator.open(); val destination = f.state.value.destination as Destination.CardCamera
            assertTrue(f.coordinator.burst.receive(destination, listOf(photo)))
            assertFalse(f.coordinator.burst.receive(destination, listOf(photo)))
            assertTrue(photo.original.exists())
            assertFalse(f.coordinator.burst.confirm(destination, emptySet()))
            assertFalse(f.coordinator.processing.value)
            assertTrue(f.coordinator.burst.cancel(destination.requestId)); assertFalse(photo.original.exists())
            assertEquals(destination, f.state.value.destination)
        } finally { f.scope.cancel(); photo.discard() }
    }
}
