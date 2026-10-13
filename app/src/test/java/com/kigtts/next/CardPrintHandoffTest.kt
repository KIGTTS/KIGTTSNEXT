package com.kigtts.next

import com.kigtts.next.state.*
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35])
class CardPrintHandoffTest {
    private val origin=CardPrintOrigin(90f,240f,540f,645f,1080f,1920f)
    @Test fun preparedPreviewHoldsItsPaperUntilFirstDrawThenReturnsOnlyOnceWithoutALocalPreview()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.SQUARE,print=true)
            val request=f.waitForPrint(source)
            assertFalse(f.coordinator.previewShown(request.requestId))
            assertFalse(f.coordinator.preparePrintPreview("wrong",origin))
            assertFalse(f.coordinator.preparePrintPreview(request.requestId,origin.copy(width=0f)))
            assertFalse(f.coordinator.preparePrintPreview(request.requestId,origin.copy(top=Float.NaN)))
            assertNull(f.coordinator.printPreviewHandoff.value)
            assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            val preview=requireNotNull(f.coordinator.printPreviewHandoff.value)
            assertEquals(request.card,preview.card);assertEquals(origin,preview.origin);assertFalse(preview.shown)
            assertEquals(request,f.coordinator.printRequest.value);assertEquals(scene,f.state.value.destination)
            assertEquals(0,f.backs);assertTrue(f.printed.isEmpty());assertTrue(f.previewed.isEmpty())
            assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin.copy(viewportWidth=1920f,viewportHeight=1080f)))
            assertSame(preview,f.coordinator.printPreviewHandoff.value)
            assertTrue(f.coordinator.previewShown(request.requestId));assertFalse(f.coordinator.previewShown(request.requestId))
            assertEquals(1,f.backs);assertEquals(listOf(request.card.id),f.previewed);assertTrue(f.printed.isEmpty())
            assertNull(f.coordinator.printRequest.value);assertTrue(requireNotNull(f.coordinator.printPreviewHandoff.value).shown)
            f.coordinator.onDestinationChanged(Destination.Cards)
            assertTrue(requireNotNull(f.coordinator.printPreviewHandoff.value).shown)
            f.coordinator.dismissPrintPreview("wrong");assertNotNull(f.coordinator.printPreviewHandoff.value)
            f.coordinator.dismissPrintPreview(request.requestId);assertNull(f.coordinator.printPreviewHandoff.value)
            assertEquals(1,f.backs);f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun coveredPreviewWaitsForItsCameraAndAnActualPopWithdrawsUnseenPaperWithoutNavigation()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.MINI,print=true)
            val request=f.waitForPrint(source);assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            val preview=f.coordinator.printPreviewHandoff.value
            f.state.value=f.state.value.copy(destination=Destination.Settings,navigationStack=listOf(Destination.Cards,scene))
            f.coordinator.onDestinationChanged(Destination.Settings)
            assertSame(preview,f.coordinator.printPreviewHandoff.value)
            assertFalse(f.coordinator.previewShown(request.requestId));assertFalse(f.coordinator.preparePrintPreview(request.requestId,origin))
            assertEquals(0,f.backs);assertTrue(f.previewed.isEmpty())
            f.state.value=f.state.value.copy(destination=scene,navigationStack=listOf(Destination.Cards))
            f.coordinator.onDestinationChanged(scene)
            assertSame(preview,f.coordinator.printPreviewHandoff.value)
            f.state.value=f.state.value.copy(destination=Destination.Cards,navigationStack=emptyList())
            f.coordinator.onDestinationChanged(Destination.Cards)
            assertNull(f.coordinator.printPreviewHandoff.value);assertNull(f.coordinator.printRequest.value)
            assertFalse(f.coordinator.previewShown(request.requestId));assertEquals(0,f.backs);f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun failedPreviewWarmupCanWithdrawAndPrepareAgainWithoutConsumingOrDuplicatingThePhoto()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo();val before=f.cards.cards.value.size
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.WIDE,print=true)
            val request=f.waitForPrint(source)
            assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            f.coordinator.dismissPrintPreview(request.requestId)
            assertNull(f.coordinator.printPreviewHandoff.value);assertEquals(request,f.coordinator.printRequest.value)
            assertEquals(scene,f.state.value.destination);assertEquals(0,f.backs)
            assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            assertTrue(f.coordinator.previewShown(request.requestId))
            assertEquals(before+1,f.cards.cards.value.size);assertEquals(1,f.backs);assertEquals(listOf(request.card.id),f.previewed)
            f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun removingUnseenPaperAbortsOnlyThatRequestAndCanCaptureAgainWithoutAStuckPendingLock()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo();val next=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.SQUARE,print=true)
            val request=f.waitForPrint(source);assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            val image=requireNotNull(f.stickers.imageFile(requireNotNull(request.card.imageId)))
            f.cards.remove(request.card.id)
            assertFalse(f.coordinator.abortPrintPreview("other-request"));assertEquals(request,f.coordinator.printRequest.value)
            assertTrue(f.coordinator.abortPrintPreview(request.requestId))
            assertNull(f.coordinator.printRequest.value);assertNull(f.coordinator.printPreviewHandoff.value)
            assertEquals(Destination.Cards,f.state.value.destination);assertEquals(1,f.backs)
            assertFalse(f.coordinator.abortPrintPreview(request.requestId));assertFalse(f.coordinator.previewShown(request.requestId))
            assertTrue(f.printed.isEmpty());assertTrue(f.previewed.isEmpty());assertTrue(image.isFile)
            f.coordinator.open();val fresh=f.state.value.destination as Destination.CardCamera
            assertNotEquals(scene.requestId,fresh.requestId)
            f.coordinator.captured(fresh,next,next,PolaroidFormat.MINI,print=true)
            val nextRequest=f.waitForPrint(next)
            assertEquals(fresh.requestId,nextRequest.requestId);assertEquals(1,f.backs);f.assertSaved(nextRequest.card)
        } finally {f.scope.cancel();source.delete();next.delete()}
    }
    @Test fun abortingCoveredPaperDoesNotPopTheChildAndClosesOnlyItsStaleCameraWhenItReturns()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.WIDE,print=true)
            val request=f.waitForPrint(source);assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            f.state.value=f.state.value.copy(destination=Destination.Settings,navigationStack=listOf(Destination.Cards,scene))
            f.coordinator.onDestinationChanged(Destination.Settings)
            assertTrue(f.coordinator.abortPrintPreview(request.requestId))
            assertNull(f.coordinator.printRequest.value);assertNull(f.coordinator.printPreviewHandoff.value)
            assertEquals(Destination.Settings,f.state.value.destination);assertEquals(0,f.backs)
            f.coordinator.onDestinationChanged(Destination.Settings);assertEquals(0,f.backs)
            f.state.value=f.state.value.copy(destination=scene,navigationStack=listOf(Destination.Cards))
            f.coordinator.onDestinationChanged(scene)
            assertEquals(Destination.Cards,f.state.value.destination);assertEquals(1,f.backs)
            f.coordinator.onDestinationChanged(Destination.Cards);f.coordinator.onDestinationChanged(scene)
            assertEquals(1,f.backs);assertFalse(f.coordinator.abortPrintPreview(request.requestId))
            assertTrue(f.printed.isEmpty());assertTrue(f.previewed.isEmpty());f.assertSaved(request.card)
            f.coordinator.open();val fresh=f.state.value.destination as Destination.CardCamera
            f.coordinator.onDestinationChanged(fresh);assertEquals(fresh,f.state.value.destination);assertEquals(1,f.backs)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun abortAfterTheFirstDrawOnlyClosesTheGlobalPreviewWithoutAnotherReturnOrReceipt()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.SQUARE,print=true)
            val request=f.waitForPrint(source);assertTrue(f.coordinator.preparePrintPreview(request.requestId,origin))
            assertTrue(f.coordinator.previewShown(request.requestId));assertEquals(1,f.backs)
            assertTrue(f.coordinator.abortPrintPreview(request.requestId));assertFalse(f.coordinator.abortPrintPreview(request.requestId))
            assertNull(f.coordinator.printPreviewHandoff.value);assertEquals(1,f.backs)
            assertEquals(listOf(request.card.id),f.previewed);assertTrue(f.printed.isEmpty());f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun anActualSourcePopForgetsDeferredAbortAndCannotPopALaterSceneWithTheOldId()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.MINI,print=true)
            val request=f.waitForPrint(source)
            f.state.value=f.state.value.copy(destination=Destination.Settings,navigationStack=listOf(Destination.Cards,scene))
            assertTrue(f.coordinator.abortPrintPreview(request.requestId));assertEquals(0,f.backs)
            f.state.value=f.state.value.copy(destination=Destination.Cards,navigationStack=emptyList())
            f.coordinator.onDestinationChanged(Destination.Cards)
            // A stale route notification or a recreated old route must not retain an obsolete abort owner.
            f.state.value=f.state.value.copy(destination=scene)
            f.coordinator.onDestinationChanged(scene)
            assertEquals(scene,f.state.value.destination);assertEquals(0,f.backs)
            assertFalse(f.coordinator.abortPrintPreview(request.requestId));f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun aFreshCaptureDoesNotReplaceTheDeferredAbortOwnerOfACoveredOldCamera()=runBlocking {
        val f=CardPrintCoordinatorTest.Fixture();val source=f.photo();val next=f.photo()
        try {
            f.coordinator.open();val oldScene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(oldScene,source,source,PolaroidFormat.SQUARE,print=true)
            val oldRequest=f.waitForPrint(source)
            f.state.value=f.state.value.copy(destination=Destination.Settings,navigationStack=listOf(Destination.Cards,oldScene))
            assertTrue(f.coordinator.abortPrintPreview(oldRequest.requestId));assertEquals(0,f.backs)
            f.coordinator.open();val fresh=f.state.value.destination as Destination.CardCamera
            f.coordinator.onDestinationChanged(fresh)
            f.coordinator.captured(fresh,next,next,PolaroidFormat.WIDE,print=true)
            val freshRequest=f.waitForPrint(next);assertEquals(fresh.requestId,freshRequest.requestId)
            assertTrue(f.coordinator.abortPrintPreview(freshRequest.requestId));assertEquals(1,f.backs)
            f.state.value=f.state.value.copy(destination=oldScene,navigationStack=listOf(Destination.Cards))
            f.coordinator.onDestinationChanged(oldScene)
            assertEquals(Destination.Cards,f.state.value.destination);assertEquals(2,f.backs)
            f.coordinator.onDestinationChanged(Destination.Cards)
            assertFalse(f.coordinator.abortPrintPreview(oldRequest.requestId));assertFalse(f.coordinator.abortPrintPreview(freshRequest.requestId))
            assertEquals(2,f.backs);f.assertSaved(oldRequest.card);f.assertSaved(freshRequest.card)
        } finally {f.scope.cancel();source.delete();next.delete()}
    }
}
