package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
class VoiceInputOverlayUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("playback", "appearance", "home-panel", "media-presentation").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val service = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.overlay", "测试输入", "仅测试")
            override val contributions = PluginContributions(gestures = listOf(GestureAction("input", "语音输入", PluginGesture.QUICK_TEXT_LONG_PRESS)))
            override val asr = object : AsrEngine {
                override val info = AsrInfo(role = AsrRole.SERVICE)
                override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
                override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
                    emit(RecognitionEvent.Started); emit(RecognitionEvent.Partial("手指仍在原按钮上"))
                    audio.collect {}; emit(RecognitionEvent.Transcript("完整识别", "", 1, "完整识别"))
                }
            }
            override suspend fun execute(actionId: String, context: PluginContext) =
                PluginOutcome.StartRecognition(RecognitionSession(descriptor.id, RecognitionPurpose.VOICE_INPUT))
        }
        val hardware = object : AudioPlatform {
            override fun hasMicrophonePermission() = true
            override fun capture(format: AudioFormat) = flow<PcmAudio> { emit(PcmAudio(ByteArray(640))); awaitCancellation() }
            override fun player() = object : PcmPlayer {
                override suspend fun write(audio: PcmAudio) {}; override suspend fun finish() {}; override fun close() {}
            }
        }
        return AppViewModel(app, listOf(service), hardware).also { compose.setContent { KigttsApp(it) } }
    }
    private fun holdDock() {
        val bounds = compose.onNodeWithTag("dock-main").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(bounds.center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
    }
    @Test fun originalHeldPointerCanSlideToCancelAfterOverlayInsertionAndHomeRemains() {
        val model = show(); holdDock()
        compose.onNodeWithTag("voice-input-live").assertIsDisplayed()
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertExists()
        compose.onNodeWithTag("voice-input-read").assertDoesNotExist()
        val cancel = compose.onNodeWithTag("voice-input-cancel").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { moveTo(cancel); up() }
        compose.waitUntil { !model.state.value.voiceInputActive }
        compose.runOnIdle { assertEquals("", model.state.value.editorText); assertEquals(Destination.Home, model.state.value.destination) }
    }
    @Test fun releasingAtTheStartingButtonSendsAndManualReadingAddsAThirdReleaseZone() {
        val model = show(); compose.runOnIdle { model.setAutoPlayback(false) }; holdDock()
        compose.onNodeWithTag("voice-input-read").assertIsDisplayed()
        compose.onNodeWithTag("voice-input-cancel").assertIsDisplayed()
        compose.onNodeWithTag("voice-input-continuous").assertIsDisplayed()
        compose.onRoot().performTouchInput { up() }
        compose.waitUntil { !model.state.value.voiceInputActive }
        compose.runOnIdle { assertEquals("完整识别", model.state.value.editorText); assertFalse(model.state.value.autoPlayback) }
    }

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun landscapeReleaseZonesFollowTheRightHandDockAndDoNotCoverTheTranscript() {
        val model = show(); compose.runOnIdle { model.setAutoPlayback(false) }; holdDock()
        val zones = compose.onNodeWithTag("voice-input-release-zones").fetchSemanticsNode().boundsInRoot
        val transcript = compose.onNodeWithTag("voice-input-text").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("voice-input-send").fetchSemanticsNode().boundsInRoot
        assertTrue(transcript.right <= zones.left)
        val targets = listOf("continuous", "cancel", "read").map {
            compose.onNodeWithTag("voice-input-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        targets.forEach { assertTrue(it.right <= send.left); assertFalse(it.overlaps(transcript)) }
        assertTrue(targets[0].bottom < targets[1].top && targets[1].bottom < targets[2].top)
        compose.onRoot().performTouchInput { moveTo(targets[1].center); up() }
        compose.waitUntil { !model.state.value.voiceInputActive }
        compose.runOnIdle { assertEquals("", model.state.value.editorText) }
    }
}
