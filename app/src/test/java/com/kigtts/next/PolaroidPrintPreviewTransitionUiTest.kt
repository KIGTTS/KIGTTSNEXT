package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.CardPrintOrigin
import com.kigtts.next.state.CardPrintRequest
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardPreviewDialog
import com.kigtts.next.ui.cards.camera.PolaroidPrinter
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidPrintPreviewTransitionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun frame() {
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
    }
    private fun handoff(format: PolaroidFormat, name: String, restoreDuringEntry: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "continuous-print-" + name + ".png")
        val pixels = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        file.outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }; pixels.recycle()
        val card = PolaroidCard(title = "同一张照片", description = "打印后继续预览", imageId = "test-blue", frameStyleId = "test")
        val style = PolaroidFrameStyle("test", "测试", format = format)
        var origin by mutableStateOf<CardPrintOrigin?>(null)
        var acknowledged by mutableStateOf(false)
        var acknowledgements = 0; var preparations = 0; var legacyCompletions = 0
        var compositionsDisposed = 0
        val restoration = StateRestorationTester(compose)
        compose.mainClock.autoAdvance = false
        restoration.setContent { KigttsTheme(ThemeSettings()) {
            DisposableEffect(Unit) { onDispose { compositionsDisposed++ } }
            Box(Modifier.fillMaxSize().background(if (acknowledged) Color(0xFF408060) else Color.White).testTag("handoff-background")) {
                if (!acknowledged) PolaroidPrinter(CardPrintRequest("continuous-" + name, card), { file }, style, true,
                    { legacyCompletions++; true }, previewHandoff = { preparations++; origin = it; true })
            }
            origin?.let { source ->
                CardPreviewDialog(card, { file }, style, {}, {}, {}, {}, {}, {},
                    printedOrigin = source, onFirstPaperDraw = { acknowledgements++; acknowledged = true },
                    printedAcknowledged = acknowledged)
            }
        } }
        try {
            repeat(220) { if (origin == null) frame() }
            val source = requireNotNull(origin)
            compose.runOnIdle { Snapshot.sendApplyNotifications() }; compose.waitForIdle()
            var creationFrames = 0
            while (compose.onAllNodesWithTag("card-preview-window").fetchSemanticsNodes().isEmpty() && creationFrames++ < 8) frame()
            compose.onNodeWithTag("card-preview-window").assertExists()
            fun paper() = compose.onNode(hasTestTag("polaroid/" + card.id) and
                hasAnyAncestor(hasTestTag("card-preview-window")), useUnmergedTree = true)
            fun paperBounds(): Rect = paper().fetchSemanticsNode().layoutInfo.coordinates.boundsInWindow()
            fun capture(stage: String): File {
                val output = File("build/previews/polaroid-print-preview-" + name + "-" + stage + ".png")
                compose.onNodeWithTag("card-preview-window").captureRoboImage(output.absolutePath)
                return output
            }
            fun bluePhoto(stage: String): Boolean {
                val output = capture(stage)
                val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
                try {
                    val window = compose.onNodeWithTag("card-preview-window").fetchSemanticsNode().boundsInRoot
                    val coordinates = paper().fetchSemanticsNode().layoutInfo.coordinates
                    val center = coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height * .4f))
                    val x = ((center.x - window.left) / window.width * bitmap.width).toInt()
                    val y = ((center.y - window.top) / window.height * bitmap.height).toInt()
                    val pixel = bitmap.getPixel(x, y)
                    return android.graphics.Color.blue(pixel) >= 220 && android.graphics.Color.red(pixel) <= 8
                } finally { bitmap.recycle() }
            }
            // No clock advance while awaiting IO: the first real preview paper must stay at
            // the printer's exact endpoint, including its already-visible photograph.
            // A blue sample alone can still come from the printer below the transparent
            // window. Await the preview's own actual draw ACK, keeping its entry clock at zero.
            var warmingFrames = 0
            while (acknowledgements == 0 && warmingFrames++ < 20) {
                compose.runOnIdle { Snapshot.sendApplyNotifications() }
                frame(); bluePhoto("warming"); ShadowLooper.idleMainLooper()
            }
            assertEquals("The preview must ACK its own real warmed photo draw", 1, acknowledgements)
            assertTrue(bluePhoto("first"))
            compose.waitForIdle()
            val first = paperBounds()
            assertEquals(source.left, first.left, 2f); assertEquals(source.top, first.top, 2f)
            assertEquals(source.width, first.width, 2f); assertEquals(source.height, first.height, 2f)
            assertEquals(1, preparations); assertEquals(0, legacyCompletions)
            assertEquals(1, acknowledgements)
            compose.runOnIdle { Snapshot.sendApplyNotifications() }; frame()
            compose.onNodeWithTag("polaroid-printer").assertDoesNotExist()
            val windowIdentity = ShadowDialog.getLatestDialog()
            repeat(13) { frame() }
            val middle = paperBounds()
            assertSame("The background return must not replace the native preview window", windowIdentity, ShadowDialog.getLatestDialog())
            assertTrue(bluePhoto("mid"))
            if (restoreDuringEntry) {
                // Restoration removes and restores content through separate snapshots. Pump
                // both changes so this really recreates the window, rather than conflating
                // them into one still-running morph under a paused animation clock.
                compose.mainClock.autoAdvance = true
                restoration.emulateSavedInstanceStateRestore()
                compose.mainClock.autoAdvance = false
                compose.runOnIdle { Snapshot.sendApplyNotifications() }; compose.waitForIdle()
                var restorationFrames = 0
                while (compose.onAllNodesWithTag("card-preview-window").fetchSemanticsNodes().isEmpty() && restorationFrames++ < 8) frame()
                assertEquals("The fixture must actually dispose the original composition", 1, compositionsDisposed)
                compose.onNodeWithTag("card-preview-window").assertExists()
                assertTrue("An ACKed window must immediately draw the photo after recreation", bluePhoto("restored-first"))
                val resumed = paperBounds()
                val resumedViewport = compose.onNodeWithTag("card-preview-content").fetchSemanticsNode().layoutInfo.coordinates.boundsInWindow()
                assertEquals("Recreation must not replay the printer's original location", resumedViewport.center.y, resumed.center.y, 1.5f)
                assertEquals(1, acknowledgements)
                compose.onNodeWithTag("share-card-preview").assertIsEnabled()
                compose.onNodeWithTag("edit-card-preview").assertIsEnabled()
                repeat(12) { frame() }
                assertEquals(resumed.top, paperBounds().top, 1.5f)
                assertEquals(resumed.width, paperBounds().width, 1.5f)
                assertEquals(1, acknowledgements)
                return
            }
            repeat(24) { frame() }
            val target = paperBounds()
            val viewport = compose.onNodeWithTag("card-preview-content").fetchSemanticsNode().layoutInfo.coordinates.boundsInWindow()
            assertEquals(viewport.center.x, target.center.x, 1.5f)
            assertEquals(viewport.center.y, target.center.y, 1.5f)
            assertEquals(format.geometry.aspectRatio, (target.width / target.height).toDouble(), .01)
            assertTrue("The paper must move continuously between its measured endpoints", middle.center.y in
                minOf(first.center.y, target.center.y)..maxOf(first.center.y, target.center.y))
            assertTrue(middle.width in minOf(first.width, target.width)..maxOf(first.width, target.width))
            assertTrue(bluePhoto("settled"))
            assertSame(windowIdentity, ShadowDialog.getLatestDialog())
            assertEquals(1, acknowledgements)
            compose.onNodeWithTag("share-card-preview").assertIsEnabled()
            compose.onNodeWithTag("edit-card-preview").assertIsEnabled()
        } finally { compose.mainClock.autoAdvance = true; file.delete() }
    }
    @Test fun thePrintedMiniPaperMovesIntoItsPreviewWhileTheBackgroundReturns() = handoff(PolaroidFormat.MINI, "portrait")
    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun thePrintedWidePaperMovesIntoItsLandscapePreviewWithoutBeingClipped() = handoff(PolaroidFormat.WIDE, "landscape")
    @Test fun recreatingAnAcknowledgedWindowDuringItsMorphResumesThePreviewWithoutRepeatingTheAck() =
        handoff(PolaroidFormat.MINI, "recreated", restoreDuringEntry = true)
}
