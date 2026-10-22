package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.LandscapeAppRail
import com.kigtts.next.ui.home.TextEditor
import com.kigtts.next.ui.navigation.AppHeader
import com.kigtts.next.ui.plugins.SpeechServicePrompt
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AutomaticReadVisibilityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun portraitReadAndAutomaticControlsHideAndRestoreTogether() = check(false)
    @Test @Config(qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun landscapeReadAndAutomaticControlsHideAndRestoreTogether() = check(true)
    private fun check(landscape: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("playback", "speech-service-guidance").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = testAppModel(app)
        val services = model.registry.plugins
        model.registry.replace(services.filter { it.tts == null })
        model.synthesisGuidance.refresh()
        compose.setContent { MaterialTheme {
            val state by model.state.collectAsState()
            if (landscape) Row(Modifier.fillMaxSize()) {
                LandscapeAppRail(state, model, {})
                TextEditor(state, model, false, false, Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                AppHeader(state, model, {})
                TextEditor(state, model, false, false, Modifier.weight(1f).fillMaxWidth())
            }
            SpeechServicePrompt(model)
        } }
        compose.onNodeWithTag("auto-playback").assertIsDisplayed().performClick()
        compose.onNodeWithText("安装语音合成服务？").assertIsDisplayed()
        compose.onNodeWithText("不安装并隐藏朗读按钮").performClick()
        compose.onNodeWithTag("auto-playback").assertDoesNotExist()
        compose.onNodeWithText("朗读").assertDoesNotExist()
        compose.onNodeWithTag("open-settings").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(model.state.value.autoPlayback)
            model.registry.replace(services)
            model.synthesisGuidance.refresh()
        }
        compose.onNodeWithTag("auto-playback").assertIsDisplayed().performClick()
        compose.onNodeWithText("朗读").assertIsDisplayed()
        compose.runOnIdle { assertTrue(model.state.value.autoPlayback) }
        if (!landscape) {
            compose.runOnIdle { model.navigate(Destination.Settings) }
            compose.onNodeWithContentDescription("返回上一页").assertIsDisplayed()
        }
    }
}
