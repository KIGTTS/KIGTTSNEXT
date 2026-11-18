package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.*
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SpeechCoordinatorTest {
    @Test fun readingEmptyTextUsesTheCurrentLanguageWithoutOpeningAudio() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var state = AppState(editorValue = androidx.compose.ui.text.input.TextFieldValue("  "))
        val messages = mutableListOf<String>()
        val hardware = object : AudioPlatform {
            override fun hasMicrophonePermission() = false
            override fun capture(format: AudioFormat): Flow<PcmAudio> = error("No recording expected")
            override fun player(): PcmPlayer = error("No audio expected")
        }
        val speech = SpeechCoordinator(this, PluginRegistry(emptyList()), hardware, { state },
            { state = it(state) }, messages::add, {}, { null },
            copy = { key, _ -> app.uiCopy(key, locale = state.uiLocale) })
        for (locale in listOf("en", "ko", "ja", "zh-CN")) {
            state = state.copy(uiLocale = locale)
            speech.speak()
        }
        assertEquals(listOf("Enter text first", "텍스트를 먼저 입력하세요", "先にテキストを入力してください", "请先输入文本"), messages)
    }

    @Test fun productionHostOnlyContainsTheBuiltInReadingServiceWithoutInstalledPackages() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app).create(AppViewModel::class.java)
        assertEquals(listOf(TtsRole.SERVICE), model.registry.plugins.mapNotNull { it.tts?.info?.role })
        assertTrue(model.registry.plugins.none { it.asr != null })
        assertTrue(model.registry.plugins.none { it.descriptor.placeholder })
    }
    @Test fun audioFlowsToSelectedPluginAndCancellationReleasesTransport() = runTest {
        var state = AppState()
        val messages = mutableListOf<String>()
        var received = 0; var released = false
        val asr = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = RecognitionResult("batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
                audio.collect { received += it.bytes.size; emit(RecognitionEvent.Partial("识别结果")) }
            }
        }
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.engine", "test", "test")
            override val asr = asr
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val hardware = object : AudioPlatform {
            override fun hasMicrophonePermission() = true
            override fun capture(format: AudioFormat) = flow {
                try { emit(PcmAudio(ByteArray(640))); awaitCancellation() } finally { released = true }
            }
            override fun player(): PcmPlayer = error("not used")
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin)), hardware, { state }, { state = it(state) }, messages::add, {}, { "test.engine" })
        controller.toggleListening(); runCurrent()
        assertEquals(640, received); assertEquals("识别结果", state.transcript); assertTrue(state.listening)
        controller.closeListening(); runCurrent()
        assertTrue(released); assertFalse(state.listening); assertFalse(state.captionsVisible)
        controller.stopAll()
    }
    @Test fun missingResourcesNeverOpenTheMicrophone() = runTest {
        var state = AppState(); var microphoneOpened = false
        val messages = mutableListOf<String>()
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.asr", "ASR", "test")
            override val asr = object : AsrEngine {
                override val info = AsrInfo(availability = EngineAvailability.NEEDS_RESOURCES, role = AsrRole.SERVICE)
                override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not used")
                override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = error("not used")
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val hardware = object : AudioPlatform {
            override fun hasMicrophonePermission() = true
            override fun capture(format: AudioFormat): Flow<PcmAudio> { microphoneOpened = true; return emptyFlow() }
            override fun player(): PcmPlayer = error("not used")
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin)), hardware, { state }, { state = it(state) }, messages::add, {}, { null })
        controller.toggleListening(); runCurrent()
        assertFalse(microphoneOpened); assertFalse(state.captionsVisible); assertTrue(messages.single().contains("模型"))
    }
    @Test fun configuredStandaloneEngineCannotBypassMissingRecognitionService() = runTest {
        var state = AppState(); var captures = 0; val messages = mutableListOf<String>()
        val provider = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.provider", "独立引擎", "test")
            override val asr = object : AsrEngine {
                override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("Provider must not be called directly")
                override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = error("Provider must not be called directly")
            }
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val audio = object : AudioPlatform {
            override fun hasMicrophonePermission() = true
            override fun capture(format: AudioFormat): Flow<PcmAudio> { captures++; return emptyFlow() }
            override fun player(): PcmPlayer = error("not used")
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(provider)), audio, { state }, { state = it(state) }, messages::add, {}, { "test.provider" })
        controller.toggleListening(); controller.voiceInput(); runCurrent()
        assertEquals(0, captures); assertFalse(state.captionsVisible); assertTrue(messages.all { it.contains("语音识别服务") })
    }
}
