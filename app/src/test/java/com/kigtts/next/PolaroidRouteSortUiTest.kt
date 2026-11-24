package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
class PolaroidRouteSortUiTest {
    @get:Rule(order = 0) val guide = CompletedGuideRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun landscapeStartsWithFourColumnsAndRotationRetainsTheDensityLevel() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val cards = (0..11).map { model.cards.create(PolaroidCard(title = "")) }.reversed()
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
        compose.onNodeWithTag("cards-browse-mode").performClick()
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "4 列"))
        val bar = compose.onNodeWithTag("small-page-bar").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("cards-grid").fetchSemanticsNode().boundsInRoot
        assertTrue("The scroll viewport must avoid the floating toolbar", grid.top >= bar.bottom)
        val first = compose.onNodeWithTag("polaroid/${cards[0].id}").fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithTag("polaroid/${cards[3].id}").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, fourth.top, 1f)
        compose.onNodeWithTag("cards-columns").performClick()
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "5 列"))
        RuntimeEnvironment.setQualifiers("zh-rCN-w393dp-h852dp-port-xxhdpi")
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "3 列"))
        RuntimeEnvironment.setQualifiers("zh-rCN-w852dp-h393dp-land-xxhdpi")
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("cards-grid").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "5 列"))
    }
    @Test fun actualRouteCanReorderWithTheGripRepeatedlyWithoutCollapsingThePhotos() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val file = File(compose.activity.cacheDir, "route-sort.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE); file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val image = model.stickers.importImage(Uri.fromFile(file))
        val album = model.cards.createAlbum("手柄排序")
        val cards = (0..5).map { model.cards.create(PolaroidCard(title = "", imageId = image.id), album.id) }.reversed()
        compose.runOnIdle { model.openSidePage(SidePage.CARDS) }
        compose.expandAlbumGroups(); compose.onNodeWithTag("card-album/${album.id}").performClick()
        compose.onNodeWithTag("toggle-card-albums").performClick()
        compose.onNodeWithTag("polaroid/${cards[0].id}").performTouchInput { longClick() }
        compose.onNodeWithTag("card-management").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        repeat(3) { cycle ->
            val pane = compose.onNodeWithTag("card-management-sort").fetchSemanticsNode().boundsInRoot
            val grip = compose.onNodeWithTag("manage-photo-grip/${cards[0].id}", true).fetchSemanticsNode().boundsInRoot
            val initial = compose.onNodeWithTag("manage-photo/${cards[1].id}").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("card-management-sort").performTouchInput {
                down(grip.center - pane.topLeft); moveBy(Offset(initial.center.x - grip.center.x, 0f), 40)
            }
            repeat(8) { frame ->
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                val neighbor = compose.onNodeWithTag("manage-photo/${cards[1].id}").fetchSemanticsNode().boundsInRoot
                assertEquals("Neighbor retains its row height during grip sorting", initial.top, neighbor.top, 4f)
                assertEquals(initial.height, neighbor.height, 4f)
                if (frame == 3) {
                    val out = File("build/previews/route-sort-$cycle.png").apply { parentFile?.mkdirs() }
                    compose.onRoot().captureRoboImage(out.absolutePath)
                }
            }
            compose.onNodeWithTag("card-management-sort").performTouchInput { up() }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1200); compose.waitForIdle()
            compose.mainClock.autoAdvance = true
            compose.waitUntil(8000) { model.stickers.snapshot.value.albums.first { it.id == album.id }.cardIds.first() == cards[if (cycle % 2 == 0) 1 else 0].id }
            compose.mainClock.autoAdvance = false
        }
    }
}
