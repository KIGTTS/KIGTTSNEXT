package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.home.SubtitleBody
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
class SubtitleAlignmentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun smallEditableTextAndPlaceholderStayCenteredAfterResizing() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        model.setAutoPlayback(false); model.text("小字幕"); model.setSubtitleSizePercent(2f); model.editing(true)
        var height by mutableIntStateOf(500)
        val window = object : WindowInfo { override val isWindowFocused = true; override val containerSize = IntSize(400, 800) }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window) {
                MaterialTheme { Box(Modifier.fillMaxSize()) { SubtitleBody(state, model, Modifier.width(350.dp).height(height.dp)) } }
            }
        }
        fun assertCentered(expectedHeight: Int) {
            val bounds = compose.onNodeWithTag("subtitle-input-glyphs", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(expectedHeight / 2f, (bounds.top.value + bounds.bottom.value) / 2f, 2f)
            assertTrue("A short input must use its natural line height", bounds.height.value < 80f)
        }
        assertCentered(500)
        compose.runOnIdle { height = 220; model.setSubtitleSizePercent(4f) }
        assertCentered(220)
        compose.runOnIdle { model.text(""); model.setSubtitlePlaceholder("空白提示") }
        assertCentered(220)
        val hint = compose.onNodeWithTag("subtitle-placeholder", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(110f, (hint.top.value + hint.bottom.value) / 2f, 2f)
    }
    @Test fun smallDisplayTextStaysCenteredWhenTheViewportAndFontSizeChange() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        model.setAutoPlayback(false); model.text("小字幕"); model.setSubtitleSizePercent(2f)
        var height by mutableIntStateOf(500)
        val window = object : WindowInfo { override val isWindowFocused = true; override val containerSize = IntSize(400, 800) }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window) {
                MaterialTheme { Box(Modifier.fillMaxSize()) {
                    SubtitleBody(state, model, Modifier.width(350.dp).height(height.dp))
                } }
            }
        }
        fun assertCentered(expectedHeight: Int) {
            val node = compose.onNodeWithTag("speech-editor")
            val bounds = node.getUnclippedBoundsInRoot()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val centerY = bounds.top.value + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
            assertEquals("Small glyphs must stay at the viewport center", expectedHeight / 2f, centerY, 2f)
            assertEquals(175f, (bounds.left.value + bounds.right.value) / 2f, 1f)
        }
        assertCentered(500)
        compose.runOnIdle { height = 220 }
        assertCentered(220)
        compose.runOnIdle { model.setSubtitleSizePercent(4f) }
        assertCentered(220)
        compose.runOnIdle { model.text(""); model.setSubtitlePlaceholder("空白提示") }
        assertCentered(220)
    }
}
