package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.SubtitleBody
import com.kigtts.plugin.api.*
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleStrokeHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class StrokePlugin : KigPlugin {
        override val descriptor = PluginDescriptor("test.strokes", "手势测试", "", apiVersion = 18)
        override val contributions = PluginContributions(gestures = listOf(GestureAction("stroke", "测试手势", PluginGesture.SUBTITLE_STROKE)))
        val strokes = mutableListOf<PluginContext>()
        override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
            if (actionId == "fullscreen") return PluginOutcome.HostAction(HostCommand.TOGGLE_SUBTITLE_FULLSCREEN)
            strokes += context; return PluginOutcome.NoAction
        }
    }
    @Test fun strokesHaveBoundedCoordinatesWhileTapLongPressPinchAndFullscreenStayNative() {
        val plugin = StrokePlugin()
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        model.setAutoPlayback(false); model.text("保持原文")
        compose.setContent { MaterialTheme { SubtitleBody(model.state.collectAsState().value, model, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("subtitle-renderer-input").performTouchInput {
            val path = listOf(Offset(width * .2f, height * .8f), Offset(width * .2f, height * .2f),
                Offset(width * .5f, height * .65f), Offset(width * .8f, height * .2f), Offset(width * .8f, height * .8f))
            down(path[0]); path.zipWithNext().forEach { (a, b) -> (1..10).forEach { step ->
                advanceEventTime(20); moveTo(a + (b - a) * (step / 10f))
            } }; up()
        }
        compose.waitUntil(5000) { plugin.strokes.isNotEmpty() }
        compose.runOnIdle {
            val input = plugin.strokes.single().inputs
            assertTrue(JSONArray(input.getValue("gesture.points")).length() in 8..512)
            assertTrue(input.getValue("gesture.durationMs").toLong() in 120..5000)
            assertEquals("保持原文", model.state.value.editorText); assertFalse(model.state.value.editing)
        }
        val before = model.state.value.subtitleSizePercent
        compose.onNodeWithTag("subtitle-renderer-input").performTouchInput {
            down(0, center - Offset(80f, 0f)); down(1, center + Offset(80f, 0f))
            moveTo(0, center - Offset(160f, 0f)); moveTo(1, center + Offset(160f, 0f))
            advanceEventTime(32); up(1); up(0)
        }
        compose.runOnIdle { assertTrue(model.state.value.subtitleSizePercent > before); assertEquals(1, plugin.strokes.size) }
        compose.onNodeWithTag("subtitle-renderer-input").performClick()
        compose.runOnIdle { assertTrue(model.state.value.editing); model.editing(false) }
        compose.onNodeWithTag("subtitle-renderer-input").performTouchInput { longClick(center) }
        compose.runOnIdle { assertTrue(model.state.value.forcedFullscreen); assertEquals(1, plugin.strokes.size) }
        compose.runOnIdle { model.invoke(plugin.descriptor.id, "fullscreen", mapOf("host.subtitleFullscreen" to "false")) }
        compose.waitUntil(5000) { model.state.value.busyPlugins.isEmpty() }
        compose.runOnIdle { assertFalse(model.state.value.forcedFullscreen) }
    }
}
