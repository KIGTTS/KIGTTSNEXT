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
    val alive get() = connection.alive
    @Volatile private var snapshot = initial
    override val descriptor get() = snapshot.descriptor
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
        override fun synthesize(request: TtsRequest) =
            synthesisPipe({ token -> connection.worker.synthesize(PluginJson.encodeToString(request), token) },
                { token -> connection.worker.cancel(token) })
    } }
    override val asr: AsrEngine? = initial.asr?.let { object : AsrEngine {
        override val info get() = requireNotNull(snapshot.asr)
        override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult =
            recognizeEvents(flowOf(audio), config, false).filterIsInstance<RecognitionEvent.Final>().last().result
        override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = recognizeEvents(audio, config, true)
        private fun recognizeEvents(audio: Flow<PcmAudio>, config: RecognitionConfig, streaming: Boolean) = channelFlow {
            val token = UUID.randomUUID().toString()
            // Ordinary pipe + explicit EOF avoids reliable-pipe status sockets being aliased
            // when its reader endpoint is transferred as an AIDL argument.
            val inputPipe = ParcelFileDescriptor.createPipe()
            val output = try { connection.worker.recognize(inputPipe[0], streaming, PluginJson.encodeToString(config), token) }
                catch (error: Exception) { inputPipe.forEach { it.close() }; throw error }
            inputPipe[0].close()
            val writer = launch(Dispatchers.IO) {
                DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(inputPipe[1])).use { data ->
                    audio.collect { AudioWire.pcm(data, it) }
                    data.writeInt(AudioWire.END_INPUT); data.flush()
                }
            }
            // Closing descriptors on cancellation unblocks a blocking pipe read/write immediately.
            val unblock = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { output.close(); inputPipe[1].close(); runCatching { connection.worker.cancel(token) } }
            }
            try {
                withContext(Dispatchers.IO) {
                    DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(output)).use { data ->
                        while (true) when (AudioWire.tag(data)) {
                            null -> break
                            AudioWire.START -> send(RecognitionEvent.Started)
                            AudioWire.PARTIAL -> send(RecognitionEvent.Partial(AudioWire.readText(data)))
                            AudioWire.FINAL -> send(RecognitionEvent.Final(PluginJson.decodeFromString(AudioWire.readText(data))))
                            AudioWire.ERROR -> AudioWire.failure(data)
                            else -> error("非法 ASR 响应")
                        }
                    }
                }
                writer.join()
            } finally { unblock.cancelAndJoin(); writer.cancelAndJoin() }
        }.flowOn(Dispatchers.IO)
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
