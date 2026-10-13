package com.kigtts.plugin.runtime

import android.os.ParcelFileDescriptor
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/** Only wire DTOs and file descriptors live in the app process. */
internal class RemotePlugin(private val connection: WorkerConnection, initial: PluginSnapshot) : KigPlugin {
    val generation: String = UUID.randomUUID().toString()
    val alive get() = connection.alive
    @Volatile private var snapshot = initial
    override val descriptor get() = snapshot.descriptor
    override val localization get() = snapshot.localization
    override val contributions get() = snapshot.contributions
    override val voiceManager: VoiceManager? = initial.tts?.takeIf { it.managesVoices }?.let { object : VoiceManager {
        override suspend fun command(command: VoiceCommand): PluginOutcome = withContext(Dispatchers.IO) {
            val result = PluginJson.decodeFromString<PluginOutcome>(connection.worker.voiceCommand(PluginJson.encodeToString(command)))
            refresh(PluginContext("", "")); result
        }
    } }
    override fun page(pageId: String, context: PluginContext) = snapshot.pages.firstOrNull { it.id == pageId }
    override val tts: TtsEngine? = initial.tts?.let { object : TtsEngine {
        override val info get() = requireNotNull(snapshot.tts)
        override suspend fun control(command: TtsControl) = withContext(Dispatchers.IO) {
            if (info.supportsControl) connection.worker.ttsControl(PluginJson.encodeToString(command))
        }
        override fun synthesize(request: TtsRequest) =
            synthesisPipe({ token -> connection.worker.synthesize(PluginJson.encodeToString(request), token) },
                { token -> connection.worker.cancel(token) })
    } }
    override val asr: AsrEngine? = initial.asr?.let { object : AsrEngine {
        override val info get() = requireNotNull(snapshot.asr)
        override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult =
            recognizeEvents(flowOf(audio), config, false).filterIsInstance<RecognitionEvent.Final>().last().result
        override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = recognizeEvents(audio, config, true)
        private fun recognizeEvents(audio: Flow<PcmAudio>, config: RecognitionConfig, streaming: Boolean) =
            recognitionPipe(audio, config, streaming, { input, live, settings, token -> connection.worker.recognize(input, live, settings, token) },
                { token -> connection.worker.cancel(token) })
    } }
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = withContext(Dispatchers.IO) {
        val outcome = PluginJson.decodeFromString<PluginOutcome>(connection.worker.execute(actionId, PluginJson.encodeToString(context)))
        refresh(context)
        outcome
    }
    suspend fun refresh(context: PluginContext) = withContext(Dispatchers.IO) {
        val next = PluginJson.decodeFromString<PluginSnapshot>(connection.worker.snapshot(PluginJson.encodeToString(context)))
        val changed = next != snapshot
        snapshot = next
        changed
    }
    override suspend fun close() { connection.stop() }
}
