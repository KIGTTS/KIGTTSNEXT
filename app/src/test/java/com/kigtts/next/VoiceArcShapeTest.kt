package com.kigtts.next

import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kigtts.next.ui.home.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VoiceArcShapeTest {
    @Test fun changingCornerRadiusKeepsTheCapsuleEndsInsteadOfSquashingItsCaps() {
        for (width in listOf(393f, 852f)) for (weights in listOf(listOf(1f, 1f), listOf(1f, 1f, 1f))) {
            for (pose in voiceArcPoses(width, weights)) for (landscape in listOf(false, true)) {
                val half = pose.thickness / 2f
                val edge = pose.start - asin(half / pose.radius)
                val point = pose.center + Offset(pose.radius * cos(edge) - 2f * sin(edge),
                    pose.radius * sin(edge) + 2f * cos(edge)) - pose.bounds.topLeft
                val local = if (landscape) Offset(point.y, pose.bounds.width - point.x) else point
                for (roundness in listOf(1f, .7f, 12f / half)) {
                    val path = outline(pose, landscape, roundness).path
                    val region = Region().apply { setPath(path.asAndroidPath(), Region(-1000, -1000, 3000, 3000)) }
                    assertTrue("Capsule end moved: $width/$landscape/$roundness", region.contains(local.x.roundToInt(), local.y.roundToInt()))
                }
            }
        }
    }

    @Test fun everyIntermediateCornerFitsItsBoundsInBothOrientationsAndWithPluginButtons() {
        for (width in listOf(213f, 393f, 852f)) for (weights in listOf(listOf(1f, 1f), List(8) { if (it % 2 == 0) .55f else 1.12f })) {
            for (pose in voiceArcPoses(width, weights)) for (landscape in listOf(false, true)) for (step in 0..20) {
                val pressedRoundness = min(12f / (pose.thickness / 2f), 1f)
                val outline = outline(pose, landscape, 1f + (pressedRoundness - 1f) * step / 20)
                // Path.getBounds includes cubic control points outside the rendered curve.
                val samples = outline.path.asAndroidPath().approximate(.01f).asList().chunked(3)
                val bounds = Rect(samples.minOf { it[1] }, samples.minOf { it[2] },
                    samples.maxOf { it[1] }, samples.maxOf { it[2] })
                val size = size(pose, landscape)
                assertTrue("$bounds exceeds $size", bounds.left >= -.1f && bounds.top >= -.1f &&
                    bounds.right <= size.width + .1f && bounds.bottom <= size.height + .1f)
                assertTrue(bounds.width > 0 && bounds.height > 0)
            }
        }
    }

    private fun size(pose: VoiceArcPose, landscape: Boolean) =
        if (landscape) Size(pose.bounds.height, pose.bounds.width) else pose.bounds.size
    private fun outline(pose: VoiceArcPose, landscape: Boolean, roundness: Float) =
        VoiceArcSegment(pose, landscape, roundness).createOutline(size(pose, landscape), LayoutDirection.Ltr, Density(1f)) as Outline.Generic
}
