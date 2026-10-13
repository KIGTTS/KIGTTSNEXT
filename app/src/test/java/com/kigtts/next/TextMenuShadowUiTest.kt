package com.kigtts.next

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.contextmenu.data.*
import androidx.compose.foundation.text.contextmenu.provider.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.components.*
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
class TextMenuShadowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() {
        val data = object : TextContextMenuDataProvider {
            override fun position(destinationCoordinates: LayoutCoordinates) = Offset(300f, 700f)
            override fun contentBounds(destinationCoordinates: LayoutCoordinates) = Rect(240f, 700f, 480f, 750f)
            override fun data() = TextContextMenuData(listOf(
                TextContextMenuItem(TextContextMenuKeys.CopyKey, "复制") { },
                TextContextMenuItem(TextContextMenuKeys.PasteKey, "粘贴") { }))
        }
        compose.setContent { MaterialTheme { ProvideIconTextContextMenu {
            val provider = LocalTextContextMenuToolbarProvider.current as IconTextMenuProvider
            LaunchedEffect(Unit) { provider.showTextContextMenu(data) }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White))
        } } }
    }

    private fun checkPixels(name: String) {
        val windowNode = compose.onNodeWithTag("text-icon-menu-window")
        val windowSemantics = windowNode.fetchSemanticsNode()
        val popupRoot = windowSemantics.root as ViewRootForTest
        val position = IntArray(2)
        popupRoot.view.getLocationOnScreen(position)
        val popupOrigin = Offset(position[0].toFloat(), position[1].toFloat())
        val window = windowSemantics.boundsInRoot.translate(popupOrigin)
        val body = compose.onNodeWithTag("text-icon-menu").fetchSemanticsNode().boundsInRoot.translate(popupOrigin)
        assertTrue(body.left > window.left && body.top > window.top && body.right < window.right && body.bottom < window.bottom)
        val output = File("build/previews/text-menu-shadow-$name.png").apply { parentFile.mkdirs() }
        windowNode.captureRoboImage(output.absolutePath)
        val image = output.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            // Roborazzi captures the composed screen when a Popup creates a second native window.
            val scaleX = image.width / compose.activity.resources.displayMetrics.widthPixels.toFloat()
            val scaleY = image.height / compose.activity.resources.displayMetrics.heightPixels.toFloat()
            val baseline = image.getPixel(1, 1)
            val guard = 1.5f * compose.activity.resources.displayMetrics.density
            var shadow = 0
            var strongestDelta = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val px = x / scaleX; val py = y / scaleY
                if (px < window.left || px > window.right || py < window.top || py > window.bottom) continue
                // Exclude edge antialiasing as well as the surface itself; a uniform popup background is not a shadow.
                if (px >= body.left - guard && px <= body.right + guard && py >= body.top - guard && py <= body.bottom + guard) continue
                val pixel = image.getPixel(x, y); val alpha = Color.alpha(pixel)
                val delta = minOf(Color.red(baseline) - Color.red(pixel), Color.green(baseline) - Color.green(pixel),
                    Color.blue(baseline) - Color.blue(pixel))
                // Entry opacity also fades the blur. Compare its actual darkening with the white
                // surrounding screen, rather than requiring a settled, fully opaque shadow color.
                if (pixel != baseline && (alpha in 1..254 || alpha == 255 && delta >= 2)) {
                    shadow++; strongestDelta = maxOf(strongestDelta, delta)
                }
            }
            assertTrue("Real elevation pixels must extend beyond the visible surface ($name: pixels=$shadow, delta=$strongestDelta)", shadow > 4)
        } finally { image.recycle() }
    }

    @Test fun realShadowIsRetainedDuringEntryAndAfterTheMenuSettles() {
        compose.mainClock.autoAdvance = false
        show()
        repeat(5) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        checkPixels("entering")
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        checkPixels("settled")
    }

    @Test fun touchingTheTransparentShadowInsetClosesTheMenuThroughItsExistingExitAnimation() {
        show()
        compose.onNodeWithTag("text-icon-menu-window").performTouchInput { click(Offset(2f, height / 2f)) }
        compose.onNodeWithTag("text-icon-menu").assertDoesNotExist()
    }
}
