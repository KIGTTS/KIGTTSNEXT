package com.kigtts.next

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.sketch.*
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
class SketchPaperUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun patternMenuUpdatesTheUnboundedCanvasAndKeepsOffPageInkVisible() {
        val state = SketchState().apply {
            initializeCanvas(IntSize(786, 1600)); color = 0xFFFF0000L; penSize = 20f
            sample(Offset(-500f, -400f), 1f, false, false, 1f, 786f, 1600f); endStroke()
            viewportPan = Offset(893f, 1200f)
        }
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(393.dp, 800.dp)) { SketchPageScaffold({}, {}) { SketchScreen(state, 0, {}, {}) } }
        } }
        compose.onNodeWithContentDescription("更多页面操作").performClick()
        compose.onNodeWithText("画布背景").performClick()
        compose.onNodeWithText("方格本").performClick(); assertEquals(SketchPaperPattern.GRID, state.pattern)
        compose.onNodeWithText("点阵").performClick(); assertEquals(SketchPaperPattern.DOTS, state.pattern)
        compose.onNodeWithText("完成").performClick(); compose.waitForIdle()
        val output = File("build/previews/sketch-infinite-dots.png").apply { parentFile?.mkdirs() }
        compose.onNodeWithTag("sketch-canvas").captureRoboImage(output.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
        try { assertEquals("Off-page ink must draw in the unbounded viewport", Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
        finally { bitmap.recycle() }
        assertEquals(SketchPaperPattern.DOTS, SketchDocumentCodec.decode(SketchDocumentCodec.encode(state.document())).pattern)
    }
}
