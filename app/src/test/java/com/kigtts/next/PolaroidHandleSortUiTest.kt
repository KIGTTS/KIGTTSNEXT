package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
@Config(sdk = [35], qualifiers = "zh-rCN-w900dp-h500dp-xhdpi")
class PolaroidHandleSortUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun handleFollowsTheFingerWithoutMovingOtherPapersToTheOrigin() = sort(false)
    @Test fun longPressUsesTheSameFloatingPaperAndDropPath() = sort(true)
    @Test fun gridHandleKeepsEveryPaperInItsCellDuringImmediateSwap() = sort(false, true)
    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-port-xxhdpi")
    fun portraitGridHandleCanCrossRowsWithoutJumpingToTheOrigin() = sort(false, true, false)
    @Test fun transientPageInteractivityCannotDropAnOwnedHandleGesture() = sort(false, interrupt = true)

    private fun sort(longPress: Boolean, grid: Boolean = false, wide: Boolean = true, interrupt: Boolean = false) {
        val file = File(compose.activity.cacheDir, "handle-sort-blue.png")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val cards = listOf("a", "b", "c", "d").map { PolaroidCard(id = it, title = "", imageId = if (it == "b") "blue" else null) }
        var keys by mutableStateOf(cards.map { it.id }); var managing by mutableStateOf(false)
        var loaded by mutableStateOf(false); var commits = 0
        var pageInteractive by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val image = rememberCardImage(file, 512); SideEffect { loaded = image != null }
            Box(Modifier.size(if (wide) 800.dp else 360.dp, if (wide) 400.dp else 680.dp).background(MaterialTheme.colorScheme.background).testTag("handle-scene")) {
                CardManagementTransition(managing, browser = {
                    PolaroidBrowser(cards, PolaroidBrowseMode.STACK, "b", { file }, emptyList(), !managing,
                        {}, {}, {}, {}, wideLayout = true)
                }, manager = {
                    CardManagementSortPane(keys, if (grid) CardManagementMode.GRID else CardManagementMode.STRIP,
                        if (wide) 4 else 2, LocalCardManagementActive.current && pageInteractive, false, "b",
                        order = { keys = it; commits++ }) { id, modifier, grip, lifted ->
                        CardManagementPhoto(cards.first { it.id == id }, id == "b", true, { file }, null, {}, modifier, grip, lifted, "b")
                    }
                })
            }
        } }
        compose.waitUntil(8000) { loaded }
        compose.runOnIdle { managing = true }; compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val pane = compose.onNodeWithTag("card-management-sort").fetchSemanticsNode().boundsInRoot
        val paper = compose.onNodeWithTag("manage-photo/b").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("manage-photo-grip/b", true).fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("manage-photo/c").fetchSemanticsNode().boundsInRoot
        val original = blueBounds("initial-$longPress")
        val start = (if (longPress) paper.center else handle.center) - pane.topLeft
        val firstDelta = if (grid) target.center - pane.topLeft - start else Offset(target.center.x - pane.left - start.x, 0f)
        compose.onNodeWithTag("card-management-sort").performTouchInput {
            down(start)
            if (longPress) { advanceEventTime(800); moveBy(Offset.Zero) }
            moveBy(firstDelta, 40)
        }
        repeat(6) { frame ->
            if (interrupt) compose.runOnIdle { pageInteractive = frame != 1 }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            val actual = blueBounds("picked-$longPress-$frame")
            assertEquals("Paper stays at its grabbed height", original.center.y + firstDelta.y, actual.center.y, 4f)
            assertEquals("Paper follows the first handle delta immediately", original.center.x + firstDelta.x, actual.center.x, 4f)
            assertEquals(original.width, actual.width, 4f)
        }
        repeat(8) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val moved = blueBounds("moved-$longPress")
        assertEquals(original.center.y + firstDelta.y, moved.center.y, 4f)
        compose.onNodeWithTag("card-management-sort").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
        assertEquals(1, commits); assertEquals(listOf("a", "c", "b", "d"), keys)
        blueBounds("dropped-$longPress")
    }

    private fun blueBounds(name: String): Rect {
        val output = File("build/previews/handle-sort-$name.png").apply { parentFile?.mkdirs() }
        compose.onNodeWithTag("handle-scene").captureRoboImage(output.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
        try {
            var l = bitmap.width; var r = 0; var t = bitmap.height; var b = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                if (Color.blue(p) > 230 && Color.red(p) < 20 && Color.green(p) < 20) {
                    l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
                }
            }
            assertTrue("Dragged photo remains visible: $name", r > l && b > t)
            return Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
        } finally { bitmap.recycle() }
    }
}
