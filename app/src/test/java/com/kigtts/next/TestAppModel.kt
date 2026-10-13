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
    listOf(PlaceholderTtsPlugin(), PlaceholderAsrPlugin(), testRecognitionService(),
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
