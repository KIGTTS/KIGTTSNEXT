package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.home.SubtitleBody
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.onStart
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleReturnReadUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun imeReturnReadsWithoutClosingAndDismissingEditingNeverReadsAgain() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app); val spoken = java.util.concurrent.CopyOnWriteArrayList<String>()
        model.registry.replace(model.registry.plugins.map { original ->
            val engine = original.tts
            if (engine == null) original else object : KigPlugin {
                override val descriptor = original.descriptor
                override val tts = object : TtsEngine {
                    override val info = engine.info
                    override fun synthesize(request: TtsRequest) = engine.synthesize(request).onStart { spoken += request.text }
                }
                override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
            }
        })
        model.setAutoPlayback(true); model.text("回车朗读"); model.editing(true)
        compose.setContent { val state by model.state.collectAsState()
            MaterialTheme { SubtitleBody(state, model, Modifier.width(350.dp).height(500.dp)) } }
        compose.onNodeWithTag("speech-editor").performImeAction()
        compose.waitUntil(5000) { spoken.size == 1 }
        compose.runOnIdle { assertTrue(model.state.value.editing); model.editing(false) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("回车朗读"), spoken.toList()) }
    }
}
