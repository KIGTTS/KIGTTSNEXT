package com.kigtts.next.identity

import androidx.compose.runtime.BroadcastFrameClock
import com.kigtts.next.ui.identity.AvatarShapeMotion
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarShapesTest {
    @Test fun allMaterialContoursAreFiniteAndShareTheExportedWebPath() {
        assertEquals(35, AvatarFrames.names.size)
        AvatarFrames.names.keys.forEach { id ->
            val svg = AvatarFrames.svgPath(id)
            assertTrue(svg.startsWith("M")); assertTrue(svg.endsWith("Z"))
            val points = AvatarFrames.points(id)
            assertTrue("${id}必须保持在裁剪框内", points.all { it.isFinite() && it >= -.001f && it <= 1.001f })
            assertEquals(svg, IdentityCard(designId = "test", avatarFrame = id).publicJson().getString("avatarClipPath"))
        }
    }
    @Test fun rapidShapeChangesContinueFromTheVisibleContour() = runBlocking {
        val clock = BroadcastFrameClock()
        val motion = AvatarShapeMotion("circle")
        var job = launch(Dispatchers.Unconfined + clock) { motion.select("flower") }
        clock.sendFrame(0); clock.sendFrame(120_000_000)
        val visible = motion.segment.points()
        assertTrue(motion.segment.progress.value > 0f && motion.segment.progress.value < 1f)
        job.cancelAndJoin()
        job = launch(Dispatchers.Unconfined + clock) { motion.select("sun") }
        assertArrayEquals("中途改选不可跳回圆形或上个目标", visible, motion.segment.from, 0f)
        assertArrayEquals(visible, motion.segment.points(), 0f)
        clock.sendFrame(140_000_000); clock.sendFrame(500_000_000)
        job.join()
        assertArrayEquals(AvatarFrames.points("sun"), motion.segment.points(), .000001f)
    }
}
