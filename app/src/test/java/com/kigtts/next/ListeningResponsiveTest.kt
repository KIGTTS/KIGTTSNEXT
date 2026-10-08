package com.kigtts.next

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ListeningResponsiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return AppViewModel(app)
    }
    @Test fun panelBecomesOneLineThenHidesAndRestoresPreferredHeightWithCenteredHandle() {
        val model = model()
        var viewport by mutableFloatStateOf(600f)
        compose.setContent { Box(Modifier.height(viewport.dp)) { KigttsApp(model) } }
        compose.runOnIdle { model.toggleListening(); model.captionHeight(190f) }
        val card = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        val indicator = compose.onNodeWithTag("listening-drag-indicator").getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        assertEquals(190f, (card.bottom - card.top).value, .5f)
        assertEquals((card.bottom + editor.top).value / 2f, (indicator.top + indicator.bottom).value / 2f, .5f)
        compose.runOnIdle { viewport = 450f }
        val compact = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        assertEquals(40f, (compact.bottom - compact.top).value, .5f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("listening-content").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().layoutInput.maxLines)
        compose.onNodeWithTag("listening-font-size").assertDoesNotExist()
        compose.onNodeWithTag("panel.listening/mode").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/listening-compact-one-line.png")
        compose.runOnIdle { viewport = 380f }
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.runOnIdle { assertTrue(model.state.value.captionsVisible); assertEquals(190f, model.state.value.captionHeight); viewport = 600f }
        val restored = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        assertEquals(190f, (restored.bottom - restored.top).value, .5f)
        compose.onNodeWithTag("listening-font-size").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/listening-restored-centered.png")
    }
    @Test fun closeLabelFollowsPanelEvenAfterRecognitionFinishes() {
        val model = model()
        compose.setContent { KigttsApp(model) }
        compose.onNodeWithText("开始聆听").performClick()
        compose.onNodeWithText("关闭聆听").assertIsDisplayed()
        compose.runOnIdle { shadowOf(Looper.getMainLooper()).idleFor(4, TimeUnit.SECONDS) }
        compose.runOnIdle { assertFalse(model.state.value.listening); assertTrue(model.state.value.captionsVisible) }
        compose.onNodeWithText("关闭聆听").performClick()
        compose.runOnIdle { assertFalse(model.state.value.captionsVisible) }
        compose.onNodeWithText("开始聆听").assertIsDisplayed()
        compose.onNodeWithTag("captions").assertDoesNotExist()
    }
    @Test fun expandingQuickSheetAlsoCompactsAndHidesListeningWithoutTurningItOff() {
        val model = model()
        compose.setContent { KigttsApp(model) }
        compose.runOnIdle { model.toggleListening(); model.captionHeight(190f); model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(490f) }
        val compact = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        assertEquals(40f, (compact.bottom - compact.top).value, .5f)
        compose.onRoot().captureRoboImage("build/previews/listening-compact-with-quick-sheet.png")
        compose.runOnIdle { model.panelHeight(600f) }
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.runOnIdle { assertTrue(model.state.value.captionsVisible); model.closePanel() }
        val restored = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        assertEquals(190f, (restored.bottom - restored.top).value, .5f)
    }
}
