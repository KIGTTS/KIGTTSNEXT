package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class PolaroidStackLayersUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theCurrentPhotoStaysInFrontWhileItsStackBecomesGridOrSortAndBack() = checkTransitions(1)
    @Test fun laterFocusedPhotoRetainsItsBackPapersWhenReturningFromTheGrid() = checkTransitions(4)
    private fun checkTransitions(focused: Int) {
        val colors = listOf(Color.GREEN, Color.GREEN, Color.RED, Color.YELLOW, Color.MAGENTA, Color.CYAN)
            .mapIndexed { index, color -> if (index == focused) Color.BLUE else color }
        val files = colors.mapIndexed { index, color -> File(compose.activity.cacheDir, "stack-layer-$index.png").apply {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        } }
        val cards = files.indices.map { PolaroidCard(id = "layer-$it", imageId = "$it", title = "") }
        var mode by mutableStateOf(PolaroidBrowseMode.STACK)
        var managing by mutableStateOf(false)
        var ready by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            val cached = files.flatMap { f -> listOf(256, 512, 1024, 1200).map { rememberCardImage(f, it) } }
            SideEffect { ready = cached.all { it != null } }
            Box(Modifier.fillMaxSize().testTag("stack-layer-scene")) {
                CardManagementTransition(managing, browser = {
                    PolaroidBrowser(cards, mode, cards[focused].id, { files[it.toInt()] }, emptyList(), !managing,
                        { mode = it }, {}, {}, {}, Modifier.fillMaxSize())
                }, manager = {
                    CardManagementSortPane(cards.map { it.id }, CardManagementMode.STRIP, 2,
                        LocalCardManagementActive.current, false, cards[focused].id, {}) { id, modifier, grip, lifted ->
                        CardManagementPhoto(cards.first { it.id == id }, id == cards[focused].id, LocalCardManagementActive.current,
                            { files[it.toInt()] }, null, {}, modifier, grip, lifted, cards[focused].id)
                    }
                })
            }
        } }
        compose.waitUntil(8000) { ready }; compose.waitForIdle(); compose.mainClock.autoAdvance = false
        fun capture(name: String): Offset {
            val nodes = compose.onAllNodesWithTag("polaroid/layer-$focused", useUnmergedTree = true).fetchSemanticsNodes()
            val coordinates = (if (managing) nodes.last() else nodes.first()).layoutInfo.coordinates
            val center = coordinates.localToRoot(Offset(coordinates.size.width * .5f, coordinates.size.height * .4f))
            val output = File("build/previews/stack-layers-$focused-$name.png").apply { parentFile?.mkdirs() }
            compose.onNodeWithTag("stack-layer-scene").captureRoboImage(output.absolutePath)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            try {
                val pixel = bitmap.getPixel(center.x.toInt().coerceIn(0, bitmap.width - 1), center.y.toInt().coerceIn(0, bitmap.height - 1))
                assertTrue("Later photos must not cover the current blue paper ($name): $pixel at $center",
                    Color.blue(pixel) > 230 && Color.red(pixel) < 20 && Color.green(pixel) < 20)
            } finally { bitmap.recycle() }
            return center
        }
        fun transition(name: String, action: () -> Unit) {
            val start = capture("$name-before")
            var previous = start
            val neighbor = "layer-${focused + 1}"
            fun decorationCorners(): Pair<Offset, Offset>? {
                val nodes = compose.onAllNodes(hasTestTag("polaroid/$neighbor") and hasAnyAncestor(hasTestTag("cards-stack")),
                    useUnmergedTree = true).fetchSemanticsNodes()
                val c = nodes.firstOrNull()?.layoutInfo?.coordinates ?: return null
                return c.localToRoot(Offset.Zero) to c.localToRoot(Offset(c.size.width.toFloat(), 0f))
            }
            val corners = if (mode == PolaroidBrowseMode.STACK) decorationCorners() else null
            compose.runOnIdle(action)
            repeat(26) { frame ->
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                if (frame in listOf(0, 1, 2, 3, 7, 13, 25)) {
                    val center = capture("$name-$frame")
                    if (frame <= 3) assertTrue("First frames cannot teleport: $name/$frame $previous -> $center",
                        (center - previous).getDistance() < 100f)
                    previous = center
                    if (frame <= 3 && corners != null && name in listOf("to-grid", "to-sort")) {
                        val next = requireNotNull(decorationCorners())
                        assertTrue("Outgoing decoration fades in place, including its angle: $name/$frame",
                            (next.first - corners.first).getDistance() < 3f && (next.second - corners.second).getDistance() < 3f)
                    }
                }
            }
        }
        capture("initial")
        transition("to-grid") { mode = PolaroidBrowseMode.GRID }
        transition("to-stack") { mode = PolaroidBrowseMode.STACK }
        transition("to-sort") { managing = true }
        transition("from-sort") { managing = false }
    }
}
