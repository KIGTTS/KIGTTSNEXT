package com.kigtts.next

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.home.AutomaticReadButton
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AutomaticReadDeviceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun longPressSelectsADeviceWithoutEnablingAutomaticRead() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("speech-audio-devices", 0).edit().clear().commit()
        app.getSharedPreferences("playback", 0).edit().clear().commit()
        val service = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.service", "test", "test")
            override val tts = object : TtsEngine {
                override val info = TtsInfo(role = TtsRole.SERVICE)
                override fun synthesize(request: TtsRequest) = kotlinx.coroutines.flow.emptyFlow<SynthesisEvent>()
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.NoAction
        }
        val speaker = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()
        shadowOf(app.getSystemService(AudioManager::class.java)).setOutputDevices(listOf(speaker))
        val model = AppViewModel(app, listOf(service))
        try {
            compose.setContent { val state by model.state.collectAsState(); KigttsTheme(ThemeSettings()) { AutomaticReadButton(state, model) } }
            compose.onNodeWithTag("auto-playback").performTouchInput { longClick() }
            compose.onNodeWithText("首选朗读设备").assertIsDisplayed()
            compose.onNodeWithText("手机扬声器").performClick()
            compose.runOnIdle { assertFalse(model.state.value.autoPlayback); assertEquals("手机扬声器", model.audioDevices.summary(false)) }
            compose.onNodeWithTag("auto-playback").performTouchInput { longClick() }
            compose.onNodeWithText("系统默认").performClick()
            compose.runOnIdle { assertNull(model.audioDevices.selected(false)); assertFalse(model.state.value.autoPlayback) }
        } finally { model.audioDevices.close() }
    }
}
