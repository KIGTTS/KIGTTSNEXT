package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PolaroidResizeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private data class PaintedFrame(val rect: Rect, val clock: Long, val paint: PolaroidPaint)
    @Test fun theFirstDrawOfANewEndpointKeepsThePaintedPaperAndRapidReversalsNeverResetIt() {
        val card = PolaroidCard(title = "第一帧", frameStyleId = "instax.mini.classic")
        val frameStyle = PolaroidFrameStyle("instax.mini.classic", "Mini", format = PolaroidFormat.MINI)
        var expanded by mutableStateOf(true)
        var coordinates: LayoutCoordinates? = null
        var motion: PolaroidResizeMotion? = null
        var density = 1f
        val draws = mutableListOf<PaintedFrame>()
        fun currentFrame(): PaintedFrame {
            val paper = requireNotNull(coordinates)
            return PaintedFrame(Rect(paper.localToRoot(Offset.Zero),
                paper.localToRoot(Offset(paper.size.width.toFloat(), paper.size.height.toFloat()))),
                compose.mainClock.currentTime, requireNotNull(motion).painted.value)
        }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            density = LocalDensity.current.density
            Column(Modifier.size(360.dp, 640.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val width = minOf(maxWidth * .78f, maxHeight * .86f * frameStyle.format.geometry.aspectRatio.toFloat())
                    val resize = rememberPolaroidResize(card.id, width, maxWidth, maxHeight)
                    motion = resize
                    PolaroidCanvas(card, { null }, Modifier.width(width).polaroidResizeLayer(resize)
                        .onPlaced { coordinates = it }.drawWithContent {
                            if (coordinates?.isAttached == true) draws += currentFrame()
                            drawContent()
                        }, frame = frameStyle)
                }
                CardAlbumDock(emptyList(), "", emptyList(), null, { null }, true, false, expanded,
                    { expanded = it }, {}, {}, {}, {}, {})
            }
        } }
        compose.onRoot().captureRoboImage("build/previews/polaroid-first-endpoint-ready.png")
        assertTrue("The fixture must draw the real paper before sampling", draws.isNotEmpty())
        val initial = currentFrame()
        val origin = initial.rect.center - Offset(initial.paint.centerX * density, initial.paint.centerY * density)
        compose.mainClock.autoAdvance = false
        fun frame() { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        repeat(4) { reversal ->
            // A graphicsLayer moves its cached content without recording drawWithContent again.
            // Read the live transform at the current clock instead of an earlier display list.
            val before = currentFrame(); val start = draws.size
            compose.runOnIdle { expanded = !expanded; Snapshot.sendApplyNotifications() }
            compose.waitForIdle()
            frame()
            compose.onRoot().captureRoboImage("build/previews/polaroid-first-endpoint-" + reversal + ".png")
            val firstDraws = draws.drop(start)
            assertTrue("A new endpoint must produce a real drawing", firstDraws.isNotEmpty())
            firstDraws.forEach { first ->
                // The same animation frame supplies both the actual layer transform and its
                // painted trajectory. A new layout endpoint must not change that trajectory.
                // An advancing clock can legitimately move the old animation before retargeting.
                assertEquals("The first draw must compensate its endpoint at the same animation time: " + first,
                    first.paint.width * density, first.rect.width, 6f)
                assertEquals("The first draw must preserve its painted center at the same animation time: " + first,
                    origin.y + first.paint.centerY * density, first.rect.center.y, 6f)
                if (first.clock == before.clock) {
                    assertEquals("Retargeting at a fixed clock must not jump the paper width", before.rect.width, first.rect.width, 6f)
                    assertEquals("Retargeting at a fixed clock must not jump the painted center", before.rect.center.y, first.rect.center.y, 6f)
                }
            }
            repeat(3) { frame() }
        }
        repeat(20) { frame() }
        compose.mainClock.autoAdvance = true
    }

    @Test fun foldingTheDockMeasuresTheRealCanvasAtEndpointsRatherThanOnAnimationFrames() {
        val card = PolaroidCard(title = "端点尺寸", frameStyleId = "instax.mini.classic")
        val frame = PolaroidFrameStyle("instax.mini.classic", "Mini", format = PolaroidFormat.MINI)
        var expanded by mutableStateOf(true); var measured = 0
        val sizes = mutableListOf<IntSize>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Column(Modifier.size(360.dp, 640.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val width = minOf(maxWidth * .78f, maxHeight * .86f * frame.format.geometry.aspectRatio.toFloat())
                    val resize = rememberPolaroidResize(card.id, width, maxWidth, maxHeight)
                    PolaroidCanvas(card, { null }, Modifier.width(width).layout { child, constraints ->
                        measured++
                        val placed = child.measure(constraints)
                        layout(placed.width, placed.height) { placed.place(0, 0) }
                    }.onSizeChanged { sizes += it }.polaroidResizeLayer(resize), frame = frame)
                }
                CardAlbumDock(emptyList(), "", emptyList(), null, { null }, true, false, expanded,
                    { expanded = it }, {}, {}, {}, {}, {})
            }
        } }
        val initialMeasures = measured
        compose.mainClock.autoAdvance = false
        fun frame() { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.runOnIdle { expanded = false; Snapshot.sendApplyNotifications() }
        repeat(4) { frame() }
        val endpointMeasures = measured
        assertTrue("Only the changed endpoint may measure the paper: " + (endpointMeasures - initialMeasures),
            endpointMeasures - initialMeasures <= 2)
        val mid = compose.onNodeWithTag("polaroid/" + card.id).fetchSemanticsNode().boundsInRoot
        assertEquals(PolaroidFormat.MINI.geometry.aspectRatio, (mid.width / mid.height).toDouble(), .01)
        compose.onRoot().captureRoboImage("build/previews/polaroid-dock-close-mid.png")
        repeat(20) { frame() }
        assertEquals("Animation frames must not measure the paper again", endpointMeasures, measured)
        assertTrue(sizes.distinct().size <= 2)
        compose.runOnIdle { expanded = true; Snapshot.sendApplyNotifications() }
        repeat(4) { frame() }
        val openMeasures = measured
        compose.onRoot().captureRoboImage("build/previews/polaroid-dock-open-mid.png")
        repeat(20) { frame() }
        assertEquals("Opening animation frames must not remeasure the paper", openMeasures, measured)
    }
}
