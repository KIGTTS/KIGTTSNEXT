package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidAlbumTransitionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Fixture(val files: Map<String, File>) {
        val blue = (0..1).map { PolaroidCard(id = "blue-" + it, title = "蓝色照片", imageId = "blue") }
        val red = (0..1).map { PolaroidCard(id = "red-" + it, title = "红色照片", imageId = "red") }
        val albums = listOf(PolaroidAlbum("a", "第一册", blue.map { it.id }), PolaroidAlbum("b", "第二册", red.map { it.id }), PolaroidAlbum("empty", "空册"))
        var selected by mutableStateOf("a")
        var focused by mutableStateOf<String?>(blue.first().id)
        var request by mutableLongStateOf(0L)
        var columns by mutableIntStateOf(2)
        var mode by mutableStateOf(PolaroidBrowseMode.GRID)
        fun select(id: String) { request++; selected = id; focused = albums.first { it.id == id }.cardIds.firstOrNull() }
    }
    private fun show(): Fixture {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val files = mapOf("blue" to android.graphics.Color.BLUE, "red" to android.graphics.Color.RED).mapValues { (id, color) ->
            File(app.cacheDir, "album-motion-" + id + ".png").apply {
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        val fixture = Fixture(files)
        var warmed by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            // Warm the same native decode bucket before pausing the test clock for real mid-frame pixels.
            val images = files.values.map { rememberCardImage(it, 1024) }
            SideEffect { warmed = images.all { it != null } }
            Box(Modifier.fillMaxSize()) {
                val all = fixture.blue + fixture.red
                val grid = all.filter { it.id in fixture.albums.first { it.id == fixture.selected }.cardIds }
                PolaroidBrowser(if (grid.isEmpty() && fixture.focused == null) emptyList() else all,
                    fixture.mode, fixture.focused, { files[it] }, emptyList(), true,
                    { fixture.mode = it }, { card -> fixture.focused = card.id; fixture.selected = fixture.albums.first { card.id in it.cardIds }.id },
                    {}, {}, Modifier.fillMaxSize(), gridCards = grid, albums = fixture.albums,
                    selectedAlbumId = fixture.selected, albumChangeRequest = fixture.request,
                    gridColumns = fixture.columns, wideLayout = false)
            }
        } }
        compose.waitUntil(8_000) {
            compose.onNodeWithTag("polaroid-browser").captureRoboImage("build/previews/polaroid-album-ready.png")
            warmed && compose.onNodeWithTag("polaroid/blue-0").fetchSemanticsNode().boundsInRoot.width > 0f
        }
        return fixture
    }
    private fun frame() { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    private fun paper(id: String, request: Long) = compose.onNode(hasTestTag("polaroid/" + id) and
        hasAnyAncestor(hasTestTag("polaroid-album-pane/" + request)), useUnmergedTree = true)
    private fun center(id: String, request: Long): Offset {
        val coordinates = paper(id, request).fetchSemanticsNode().layoutInfo.coordinates
        return coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height * .4f))
    }
    private fun pixel(file: File, point: Offset): Int {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            val viewport = compose.onNodeWithTag("polaroid-browser").fetchSemanticsNode().boundsInRoot
            assertTrue("The transition sample must stay inside the actual browser", viewport.contains(point))
            return bitmap.getPixel(((point.x - viewport.left) / viewport.width * bitmap.width).toInt(),
                ((point.y - viewport.top) / viewport.height * bitmap.height).toInt())
        } finally { bitmap.recycle() }
    }
    @Test fun selectingTheNextAlbumKeepsBothActualPhotographsVisibleDuringTheSlideAndEndsAtItsFocus() {
        val f = show()
        val oldSecond = center("blue-1", 0); val firstColumn = center("blue-0", 0)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { f.select("b"); Snapshot.sendApplyNotifications() }
        var overlap = false
        repeat(14) {
            if (!overlap) {
                frame()
                val viewport = compose.onNodeWithTag("polaroid-browser").fetchSemanticsNode().boundsInRoot
                overlap = viewport.contains(center("blue-1", 0)) && viewport.contains(center("red-0", 1))
            }
        }
        assertTrue("The album slide must have a real middle frame with both photo centers visible", overlap)
        val outgoing = center("blue-1", 0); val incoming = center("red-0", 1)
        assertTrue(outgoing.x < oldSecond.x - 10f)
        assertTrue(incoming.x > firstColumn.x + 10f)
        val image = File("build/previews/polaroid-album-next-mid.png")
        compose.onNodeWithTag("polaroid-browser").captureRoboImage(image.absolutePath)
        val blue = pixel(image, outgoing); val red = pixel(image, incoming)
        assertTrue("The exiting album retains its blue photograph pixels", android.graphics.Color.blue(blue) > android.graphics.Color.red(blue) + 32)
        assertTrue("The incoming album renders its red photograph before the old one disappears", android.graphics.Color.red(red) > android.graphics.Color.blue(red) + 32)
        paper("blue-1", 0).assertIsNotEnabled()
        repeat(20) { frame() }
        compose.onNodeWithTag("polaroid/blue-0").assertDoesNotExist()
        compose.onNodeWithTag("polaroid/red-0").assertIsDisplayed()
        assertEquals("b", f.selected); assertEquals("red-0", f.focused)
        compose.onNodeWithTag("polaroid-browser").captureRoboImage("build/previews/polaroid-album-next-settled.png")
        compose.mainClock.autoAdvance = true
    }
    @Test fun rapidAlbumReversalsAndAnEmptyAlbumNeverPublishTheOutgoingFocusOrReplayOnSameAlbumChanges() {
        val f = show(); f.mode = PolaroidBrowseMode.STACK; compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { f.select("b"); Snapshot.sendApplyNotifications() }; repeat(4) { frame() }
        compose.runOnIdle { f.select("empty"); Snapshot.sendApplyNotifications() }; repeat(3) { frame() }
        compose.onNodeWithText("从一张照片开始").assertExists()
        compose.runOnIdle { f.select("a"); Snapshot.sendApplyNotifications() }; repeat(22) { frame() }
        compose.onNodeWithTag("cards-position").assertTextEquals("1 / 4")
        assertEquals("a", f.selected); assertEquals("blue-0", f.focused)
        compose.onNodeWithTag("polaroid-album-pane/3").assertExists()
        compose.onNodeWithTag("polaroid-album-pane/0").assertDoesNotExist()
        compose.onNodeWithTag("polaroid-album-pane/1").assertDoesNotExist()
        val identity = compose.onNodeWithTag("cards-stack").fetchSemanticsNode().id
        compose.runOnIdle { f.columns = 3; Snapshot.sendApplyNotifications() }; frame()
        assertEquals(identity, compose.onNodeWithTag("cards-stack").fetchSemanticsNode().id)
        assertEquals(3L, f.request)
        compose.onNodeWithTag("polaroid-browser").captureRoboImage("build/previews/polaroid-album-rapid-return.png")
        compose.mainClock.autoAdvance = true
    }
}
