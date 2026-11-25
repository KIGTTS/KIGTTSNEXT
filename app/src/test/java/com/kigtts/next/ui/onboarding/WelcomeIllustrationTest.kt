package com.kigtts.next.ui.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.caverock.androidsvg.SVG
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeIllustrationTest {
    @Test fun assembledNativeLayersMatchTheNeutralVector() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = context.assets.open("onboarding/welcome.svg").bufferedReader().use { it.readText() }
        assertFalse(source.contains("<script"))
        val svg = SVG.getFromString(source)
        val still = svg.renderToPicture(WELCOME_ART_WIDTH, WELCOME_ART_HEIGHT)
        val artwork = recordWelcomeLayers(source, svg, still)
        assertEquals(5, artwork.layers.count { it.mesh != null })
        fun pixels(vararg pictures: Picture): IntArray {
            val w = 394
            val h = 300
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                scale(w.toFloat() / WELCOME_ART_WIDTH, h.toFloat() / WELCOME_ART_HEIGHT)
                pictures.forEach(::drawPicture)
            }
            return IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        }
        val original = pixels(still)
        val parts = artwork.layers.map { it.picture } + artwork.eyes.flatMap { listOf(it.white, it.iris, it.lash) }
        val rebuilt = pixels(*parts.toTypedArray())
        val coverage = original.count { it ushr 24 > 127 }
        assertTrue(coverage > 20_000)
        val ear = pixels(artwork.layers.first { it.part == WelcomePart.LEFT_EAR }.picture).count { it ushr 24 > 127 }
        assertTrue(ear in 100..(coverage / 3))
        val changed = original.indices.count { original[it] != rebuilt[it] }
        assertTrue("Layered neutral pose changed $changed pixels", changed < coverage * .12f)
        val mesh = requireNotNull(artwork.layers.first { it.part == WelcomePart.LEFT_EAR }.mesh)
        fun meshPixels(bend: WelcomeBend): IntArray {
            val bitmap = Bitmap.createBitmap(394, 300, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                scale(.25f, .25f)
                mesh.draw(this, mesh.newVertices(), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG), bend)
            }
            return IntArray(394 * 300).also { bitmap.getPixels(it, 0, 394, 0, 0, 394, 300) }
        }
        val neutral = meshPixels(WelcomeBend())
        val pulled = meshPixels(WelcomeBend(78f, 40f))
        assertTrue(neutral.indices.count { neutral[it] != pulled[it] } > 250)
        val vertex = Offset(862f, 110f)
        assertEquals(vertex, deformWelcomePoint(vertex, vertex, Offset(-1f, 0f), 420f, WelcomeBend(78f, 40f)))
    }

    @Test fun entranceBouncesAndTouchHandsOffToTheTilt() {
        val motion = WelcomeMotion()
        motion.step(.016f, Offset.Zero, false)
        assertTrue(motion.pose.entranceY > .8f)
        repeat(22) { motion.step(.016f, Offset.Zero, false) }
        assertTrue(motion.pose.entranceY < 0f)
        repeat(20) { motion.step(.016f, Offset(.4f, 0f), false) }
        assertTrue(motion.pose.aim.x > .8f)
        assertTrue(motion.press(Offset(680f, 325f)))
        motion.drag(Offset(555f, 371f))
        repeat(18) { motion.step(.016f, Offset(.4f, 0f), false) }
        assertTrue(motion.pose.squeeze)
        assertTrue(motion.pose.aim.x < -.6f)
        assertTrue(kotlin.math.abs(motion.pose.leftEar) > 1f)
        assertTrue(kotlin.math.abs(motion.pose.leftStretch) > 4f)
        motion.release()
        repeat(48) { motion.step(.016f, Offset(.4f, 0f), false) }
        val tilted = motion.pose.aim.x
        assertTrue(tilted > .2f)
        repeat(240) { motion.step(.016f, Offset(.4f, 0f), false) }
        assertEquals(0f, motion.pose.aim.x, .05f)

        val downTilt = Offset(.4f, .5f)
        repeat(20) { motion.step(.016f, downTilt, false) }
        assertTrue(motion.pose.aim.y < -.8f)
        repeat(155) { motion.step(.016f, downTilt, false) }
        val beforeSettled = kotlin.math.abs(motion.pose.aim.y)
        assertTrue(beforeSettled > .2f)
        repeat(58) { motion.step(.016f, downTilt, false) }
        assertTrue(kotlin.math.abs(motion.pose.aim.y) < beforeSettled * .65f)
        repeat(140) { motion.step(.016f, downTilt, false) }
        assertEquals(0f, motion.pose.aim.y, .05f)
    }
}
