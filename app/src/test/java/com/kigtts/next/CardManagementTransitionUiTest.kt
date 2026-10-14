package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.PolaroidCard
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
class CardManagementTransitionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Fixture {
        var managing by mutableStateOf(false)
        var geometry: CardManagementGeometry? = null
        var ready by mutableStateOf(false)
        val sizes = mutableMapOf<String, MutableSet<IntSize>>()
        fun enter() { geometry?.begin("focus"); managing = true }
    }
    private fun show(): Fixture {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val colors = mapOf("left" to android.graphics.Color.GREEN, "focus" to android.graphics.Color.BLUE, "right" to android.graphics.Color.RED)
        val files = colors.mapValues { (id, color) -> File(app.cacheDir, "management-" + id + ".png").apply {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        } }
        val f = Fixture()
        val cards = colors.keys.map { PolaroidCard(id = it, imageId = it, title = it) }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val images = files.values.flatMap { listOf(rememberCardImage(it, 512), rememberCardImage(it, 1024)) }
            SideEffect { f.ready = images.all { it != null } }
            Box(Modifier.fillMaxSize()) {
                CardManagementTransition(f.managing, browser = {
                    val geometry = LocalCardManagementGeometry.current
                    SideEffect { f.geometry = geometry }
                    Box(Modifier.fillMaxSize().testTag("management-browser"), contentAlignment = Alignment.Center) {
                        PolaroidCanvas(cards[1], { files[it] }, Modifier.width(300.dp).cardManagementSource("focus")
                            .onSizeChanged { f.sizes.getOrPut("large") { mutableSetOf() }.add(it) })
                    }
                }, manager = {
                    val reveal = LocalCardManagementReveal.current
                    Row(Modifier.fillMaxSize().testTag("management-strip"), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically) {
                        cards.forEachIndexed { index, card ->
                            PolaroidCanvas(card, { files[it] }, Modifier.width(92.dp)
                                .cardManagementPaper(index, 1, reveal, 92.dp, 16.dp, card.id)
                                .onSizeChanged { f.sizes.getOrPut(card.id) { mutableSetOf() }.add(it) })
                        }
                    }
                })
                TextButton({ if (f.managing) f.managing = false else f.enter() }, Modifier.align(Alignment.TopEnd).testTag("toggle-management")) { Text("管理") }
            }
        } }
        compose.waitUntil(8_000) { f.ready }
        return f
    }
    private fun frame() { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    private fun paper(id: String, container: String) = compose.onNode(hasTestTag("polaroid/" + id) and
        hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true)
    private fun paintedWidth(id: String, container: String): Float {
        val paper = paper(id, container).fetchSemanticsNode().layoutInfo.coordinates
        return (paper.localToRoot(Offset(paper.size.width.toFloat(), 0f)) - paper.localToRoot(Offset.Zero)).getDistance()
    }
    private fun center(id: String, container: String): Offset {
        val paper = paper(id, container).fetchSemanticsNode().layoutInfo.coordinates
        return paper.localToRoot(Offset(paper.size.width / 2f, paper.size.height * .4f))
    }
    private fun capture(name: String): File = File("build/previews/polaroid-management-" + name + ".png").also {
        // Shared papers paint in the parent overlay, outside the retained endpoint subtree.
        compose.onRoot().captureRoboImage(it.absolutePath)
    }
    @Test fun theRealLargePaperAndSelectedSmallPaperShareTheirMidPoseWhileNeighborsSpreadAndThenReverse() {
        val f = show(); val initial = paintedWidth("focus", "management-browser")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { f.enter(); Snapshot.sendApplyNotifications() }; repeat(9) { frame() }
        val large = paintedWidth("focus", "management-browser"); val selected = paintedWidth("focus", "management-strip")
        assertTrue(large < initial - 30f)
        assertEquals("Shared paper endpoints must follow the same continuous bounds", large, selected, 1.5f)
        val old = center("focus", "management-browser"); val current = center("focus", "management-strip")
        assertEquals(old.x, current.x, 1.5f); assertEquals(old.y, current.y, 1.5f)
        assertTrue(center("left", "management-strip").x < current.x - 10f)
        assertTrue(center("right", "management-strip").x > current.x + 10f)
        val file = capture("enter-mid")
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            val viewport = compose.onNodeWithTag("card-management-transition").fetchSemanticsNode().boundsInRoot
            val color = bitmap.getPixel(((current.x - viewport.left) / viewport.width * bitmap.width).toInt(),
                ((current.y - viewport.top) / viewport.height * bitmap.height).toInt())
            assertTrue("The selected photo must visibly survive the container transform", android.graphics.Color.blue(color) > android.graphics.Color.red(color) + 80)
        } finally { bitmap.recycle() }
        repeat(20) { frame() }
        val small = paintedWidth("focus", "management-strip")
        assertTrue(small < initial / 2f)
        compose.runOnIdle { f.managing = false; Snapshot.sendApplyNotifications() }; repeat(7) { frame() }
        assertTrue(paintedWidth("focus", "management-strip") > small + 30f)
        assertEquals(paintedWidth("focus", "management-browser"), paintedWidth("focus", "management-strip"), 1.5f)
        capture("exit-mid")
        repeat(22) { frame() }
        compose.onNodeWithTag("management-strip").assertDoesNotExist()
        assertEquals(initial, paintedWidth("focus", "management-browser"), 1.5f)
        assertTrue("Only painted layers move; each paper retains one endpoint layout size", f.sizes.values.all { it.size == 1 })
        compose.mainClock.autoAdvance = true
    }
    @Test fun aQuickEnterExitEnterContinuesItsPaperPoseWithoutResettingOrResizingTheLayouts() {
        val f = show(); val initial = paintedWidth("focus", "management-browser")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { f.enter(); Snapshot.sendApplyNotifications() }; repeat(7) { frame() }
        val before = paintedWidth("focus", "management-strip")
        compose.runOnIdle { f.managing = false; Snapshot.sendApplyNotifications() }; compose.waitForIdle()
        assertEquals(before, paintedWidth("focus", "management-strip"), 1.5f)
        repeat(3) { frame() }
        val reversed = paintedWidth("focus", "management-strip")
        compose.runOnIdle { f.managing = true; Snapshot.sendApplyNotifications() }; compose.waitForIdle()
        assertEquals(reversed, paintedWidth("focus", "management-strip"), 1.5f)
        repeat(24) { frame() }
        assertTrue(paintedWidth("focus", "management-strip") < initial / 2f)
        assertTrue(f.sizes.values.all { it.size == 1 })
        capture("rapid-settled")
        compose.mainClock.autoAdvance = true
    }
}
