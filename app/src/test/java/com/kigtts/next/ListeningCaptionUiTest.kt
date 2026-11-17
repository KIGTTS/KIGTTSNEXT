package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.home.ListeningCaptionContent
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
class ListeningCaptionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun layout(): TextLayoutResult {
        val values = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("listening-content").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
        return values.single()
    }
    private fun scroll(): Pair<Float, Float> {
        val range = compose.onNodeWithTag("listening-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() to range.maxValue()
    }
    @Test fun wrapsAtPanelWidthAndCentersShortCaptionsVertically() {
        var text by mutableStateOf("短句")
        compose.setContent { MaterialTheme { Box(Modifier.size(180.dp, 120.dp)) {
            ListeningCaptionContent(text, 18f, false, Modifier.size(180.dp, 120.dp))
        } } }
        val initial = compose.onNodeWithTag("listening-content").getUnclippedBoundsInRoot()
        assertEquals(60f, (initial.top.value + initial.bottom.value) / 2f, 1f)
        compose.runOnIdle { text = "这是一段没有手动换行的连续识别字幕。".repeat(8) }
        assertTrue(layout().lineCount > 2); assertFalse(layout().didOverflowWidth)
        val (position, maximum) = scroll(); assertTrue(maximum > 0f); assertEquals(maximum, position, 1f)
    }
    @Test fun aFinalOnlyCaptionDimsHistoryButKeepsTheLatestSentenceOpaque() {
        val transcript = "上一句\n最新一句"
        compose.setContent { MaterialTheme {
            ListeningCaptionContent(transcript, 18f, false, Modifier.size(200.dp, 120.dp), finalOnly = true)
        } }
        val input = layout().layoutInput
        assertEquals(transcript.indexOf('\n'), input.text.spanStyles.single().end)
        assertTrue(input.text.spanStyles.single().item.color.alpha < 1f)
        assertEquals(1f, input.style.color.alpha, .01f)
    }
    @Test fun resizingRewrapsAndLargeFontsKeepSeparateLines() {
        var width by mutableStateOf(180.dp)
        compose.setContent { MaterialTheme {
            ListeningCaptionContent("自动换行的连续聆听字幕".repeat(8), 40f, false, Modifier.size(width, 120.dp))
        } }
        val narrow = layout()
        assertTrue(narrow.getLineBaseline(1) - narrow.getLineBaseline(0) >= 40f)
        assertEquals(scroll().second, scroll().first, 1f)
        compose.runOnIdle { width = 320.dp }
        assertTrue(layout().lineCount < narrow.lineCount)
        assertFalse(layout().didOverflowWidth)
        assertEquals(scroll().second, scroll().first, 1f)
    }
    @Test fun partialUpdatesRespectManualHistoryAndResumeAfterReturningToBottom() {
        var text by mutableStateOf((1..40).joinToString("\n") { "第 $it 句识别字幕" })
        compose.setContent { MaterialTheme { ListeningCaptionContent(text, 18f, false, Modifier.size(200.dp, 120.dp)) } }
        assertEquals(scroll().second, scroll().first, 1f)
        compose.onNodeWithTag("listening-content").performTouchInput { swipeDown() }
        val history = scroll().first
        assertTrue(history < scroll().second)
        compose.runOnIdle { text += "\n新一句临时识别结果" }
        assertEquals(history, scroll().first, 1f)
        repeat(4) { compose.onNodeWithTag("listening-content").performTouchInput { swipeUp() } }
        // Accessibility scrolling can reach the tail deterministically without changing panel height.
        compose.onNodeWithTag("listening-content").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle(); assertEquals(scroll().second, scroll().first, 1f)
        compose.runOnIdle { text += "\n再次追加的新字幕" }
        assertEquals(scroll().second, scroll().first, 1f)
    }
    @Test fun compactSingleLineKeepsEllipsisWithoutVerticalScrolling() {
        val latest = "很长的单行紧凑字幕".repeat(8)
        compose.setContent { MaterialTheme { ListeningCaptionContent("已经固化的历史字幕\n" + latest, 30f, true, Modifier.size(200.dp, 40.dp)) } }
        assertEquals(1, layout().lineCount)
        assertEquals(latest, layout().layoutInput.text.text)
        assertEquals(18f, layout().layoutInput.style.fontSize.value, .01f)
        assertFalse(compose.onNodeWithTag("listening-content").fetchSemanticsNode().config.contains(SemanticsProperties.VerticalScrollAxisRange))
    }
}
