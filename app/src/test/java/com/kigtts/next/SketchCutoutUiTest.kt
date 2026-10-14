package com.kigtts.next

import android.graphics.Rect
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.*
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.sketch.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w1000dp-h1000dp-xhdpi")
class SketchCutoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hiddenSystemBarsRetainCutoutProtectionOnEveryEdgeWithoutShrinkingOrBlockingTheCanvas() {
        var width by mutableStateOf(393.dp); var height by mutableStateOf(800.dp)
        val documentSize = IntSize(786, 1600)
        val state = SketchState().apply { initializeCanvas(documentSize) }
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(width, height)) {
            SketchPageScaffold({}, {}) { SketchScreen(state, 0, {}, {}) }
        } } }
        fun insets(left: Int, top: Int, right: Int, bottom: Int, bounds: Rect) = compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            val density = compose.activity.resources.displayMetrics.density
            fun px(dp: Int) = (dp * density).toInt()
            val safe = Insets.of(px(left), px(top), px(right), px(bottom))
            val cutout = DisplayCutoutCompat(Rect(safe.left, safe.top, safe.right, safe.bottom),
                listOf(Rect(px(bounds.left), px(bounds.top), px(bounds.right), px(bounds.bottom))))
            val value = WindowInsetsCompat.Builder().setDisplayCutout(cutout)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), safe)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.systemBars(), false).build()
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), value)
        }
        fun verify(left: Int, top: Int, right: Int, bottom: Int) {
            val page = compose.onNodeWithTag("sketch-fullscreen-page").getUnclippedBoundsInRoot()
            assertEquals(page, compose.onNodeWithTag("sketch-canvas").getUnclippedBoundsInRoot())
            val controls = listOf(compose.onNodeWithTag("page-back"), compose.onNodeWithTag("sketch-album-open"),
                compose.onNodeWithTag("sketch-reset-view"), compose.onNodeWithContentDescription("更多页面操作")) +
                listOf("画笔", "橡皮擦", "画笔颜色", "保存画板", "清空画板").map { compose.onNodeWithContentDescription(it) }
            controls.forEach { control ->
                control.assertIsDisplayed()
                val box = control.getUnclippedBoundsInRoot()
                assertTrue("$box must avoid cutout in $page", box.left >= page.left + left.dp && box.right <= page.right - right.dp &&
                    box.top >= page.top + top.dp && box.bottom <= page.bottom - bottom.dp)
            }
            assertEquals(documentSize, state.documentSize)
        }
        insets(0, 48, 0, 0, Rect(164, 0, 229, 48)); verify(0, 48, 0, 0)
        compose.onNodeWithTag("sketch-canvas").performTouchInput {
            swipe(Offset(24f * density, 24f * density), Offset(120f * density, 24f * density), 300)
        }
        assertTrue("The empty area beside the notch remains drawable", state.strokes.any { it.points.size > 1 })
        val strokes = state.strokes.toList()
        compose.runOnIdle { width = 800.dp; height = 393.dp }
        insets(48, 0, 0, 0, Rect(0, 164, 48, 229)); verify(48, 0, 0, 0)
        insets(0, 0, 48, 0, Rect(752, 164, 800, 229)); verify(0, 0, 48, 0)
        compose.onRoot().captureRoboImage("build/previews/sketch-landscape-cutout.png")
        compose.runOnIdle { width = 393.dp; height = 800.dp }
        insets(0, 0, 0, 48, Rect(164, 752, 229, 800)); verify(0, 0, 0, 48)
        assertEquals(strokes, state.strokes.toList())
        compose.onNodeWithContentDescription("橡皮擦").performTouchInput { click() }
        assertEquals(SketchTool.ERASER, state.tool)
        assertEquals("Toolbar touches cannot mark the canvas", strokes, state.strokes.toList())
    }
}
