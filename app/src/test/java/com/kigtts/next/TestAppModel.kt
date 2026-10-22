package com.kigtts.next

import android.app.Application
import com.kigtts.next.audio.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import com.kigtts.plugins.asr.PlaceholderAsrPlugin
import com.kigtts.plugins.tts.PlaceholderTtsPlugin
import com.kigtts.plugins.listening.ListeningPanelPlugin
import com.kigtts.plugins.voice.VoiceInputPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow

/** Demo engines and synthetic PCM exist only on the test classpath. */
fun testAppModel(application: Application) = AppViewModel(application,
    listOf(testUiDebugPlugin(application), testSynthesisService(), PlaceholderAsrPlugin(), testRecognitionService(),
        ListeningPanelPlugin(), VoiceInputPlugin(application.getSharedPreferences("voice-input", 0).getBoolean("enabled", false)) {
            application.getSharedPreferences("voice-input", 0).edit().putBoolean("enabled", it).apply()
        }), object : AudioPlatform {
        override fun hasMicrophonePermission() = true
        override fun capture(format: AudioFormat) = flow {
            repeat(4) { delay(650); emit(PcmAudio(ByteArray(3200), format.sampleRate, format.channels)) }
        }
        override fun player() = object : PcmPlayer {
            override suspend fun write(audio: PcmAudio) {}
            override suspend fun finish() {}
            override fun close() {}
        }
    })

/** Only tests inject UI probes. Production discovers the separately installed debug.ui package. */
private fun testUiDebugPlugin(application: Application): KigPlugin {
    val debug = application.getSharedPreferences("ui-debug", android.content.Context.MODE_PRIVATE)
    return com.kigtts.plugins.debug.UiDebugPlugin(
        UiSlot.entries.associateWith { debug.getInt(it.name, 0) },
        { counts -> debug.edit().apply { counts.forEach { (slot, count) -> putInt(slot.name, count) } }.apply() },
        UiSlot.entries.associateWith { if (debug.getBoolean(it.name + "_text", false)) UiButtonStyle.ICON_TEXT else UiButtonStyle.ICON },
        { styles -> debug.edit().apply { styles.forEach { (slot, style) -> putBoolean(slot.name + "_text", style == UiButtonStyle.ICON_TEXT) } }.apply() })
}

private fun testSynthesisService() = object : KigPlugin {
    override val descriptor = PluginDescriptor("test.synthesis-service", "测试合成服务", "仅用于界面回归", placeholder = true)
    private val provider = requireNotNull(PlaceholderTtsPlugin().tts)
    override val tts = object : TtsEngine {
        override val info get() = provider.info.copy(role = TtsRole.SERVICE)
        override fun synthesize(request: TtsRequest) = provider.synthesize(request)
    }
    override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
}

/** A test-only service facade keeps UI fixtures on the same service-first contract as production. */
private fun testRecognitionService() = object : KigPlugin {
    override val descriptor = PluginDescriptor("test.recognition-service", "测试识别服务", "仅用于界面回归", placeholder = true)
    private val provider = requireNotNull(PlaceholderAsrPlugin().asr)
    override val asr = object : AsrEngine {
        override val info get() = provider.info.copy(role = AsrRole.SERVICE)
        override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = provider.recognize(audio, config)
        override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = provider.recognizeStream(audio, config)
    }
    override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
}
