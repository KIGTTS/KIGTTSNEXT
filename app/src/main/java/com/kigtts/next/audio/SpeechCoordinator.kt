package com.kigtts.next.audio

import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

class SpeechCoordinator(private val scope: CoroutineScope, private val registry: PluginRegistry,
    private val audio: AudioPlatform, private val state: () -> AppState,
    private val update: ((AppState) -> AppState) -> Unit, private val message: (String) -> Unit,
    private val replaceText: (String) -> Unit, private val selection: (Boolean) -> String?) {
    private var recognition: Job? = null
    private var speech: Job? = null
    private var voice: Job? = null
    private var microphoneGeneration = 0
    private var pending: (() -> Unit)? = null
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val permissionRequests = requests.receiveAsFlow()
    fun permissionResult(granted: Boolean) {
        val action = pending; pending = null
        if (granted) action?.invoke() else message("未获得麦克风权限，无法开始录音")
    }
    private fun microphone(action: () -> Unit) {
        if (audio.hasMicrophonePermission()) action() else { pending = action; requests.trySend(Unit) }
    }
    private fun asr(): AsrEngine? {
        val engine = registry.plugin(selection(false).orEmpty())?.asr
            ?: registry.plugins.firstNotNullOfOrNull { it.asr }
        if (engine == null) message("请先安装并选择语音识别插件")
        else if (engine.info.availability != EngineAvailability.READY) { message("识别插件尚未配置模型，请打开插件设置"); return null }
        return engine
    }
    fun toggleListening() {
        if (state().captionsVisible) { closeListening(); return }
        val engine = asr() ?: return
        val streaming = state().streaming
        if (streaming && engine.info.streaming == StreamingKind.NONE || !streaming && !engine.info.batch) {
            message("当前插件不支持此识别方式"); return
        }
        microphone {
            val generation = ++microphoneGeneration
            val previous = listOfNotNull(voice, recognition)
            previous.forEach { it.cancel() }
            recognition = scope.launch {
                previous.joinAll()
                if (generation != microphoneGeneration) return@launch
                update { it.copy(listening = true, captionsVisible = true, transcript = "") }
                try {
                    if (streaming) engine.recognizeStream(audio.capture(engine.info.inputFormat)).collect { event ->
                        when (event) {
                            is RecognitionEvent.Partial -> update { it.copy(transcript = event.text) }
                            is RecognitionEvent.Final -> update { it.copy(transcript = event.result.text) }
                            RecognitionEvent.Started -> Unit
                        }
                    } else {
                        val input = recording(engine.info.inputFormat, minOf(8, engine.info.maxBatchSeconds))
                        val result = engine.recognize(input)
                        update { it.copy(transcript = result.text) }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { message(error.message ?: "语音识别失败") }
                finally { if (generation == microphoneGeneration) update { it.copy(listening = false) } }
            }
        }
    }
    fun closeListening() { pending = null; microphoneGeneration++; recognition?.cancel(); update { it.copy(captionsVisible = false, listening = false) } }
    fun voiceInput() {
        val engine = asr() ?: return
        if (!engine.info.batch) { message("此插件不支持完整录音识别"); return }
        microphone {
            closeListening()
            val generation = ++microphoneGeneration
            val previous = listOfNotNull(voice, recognition)
            previous.forEach { it.cancel() }
            voice = scope.launch {
                previous.joinAll()
                if (generation != microphoneGeneration) return@launch
                message("开始语音输入，录音最多 5 秒")
                try {
                    val result = engine.recognize(recording(engine.info.inputFormat, minOf(5, engine.info.maxBatchSeconds)))
                    if (result.text.isNotBlank()) replaceText(result.text) else message("没有识别到文字")
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { message(error.message ?: "语音输入失败") }
            }
        }
    }
    private suspend fun recording(format: AudioFormat, seconds: Int): PcmAudio {
        val bytes = ByteArrayOutputStream()
        val maximum = format.sampleRate * format.channels * 2 * seconds
        audio.capture(format).takeWhile { bytes.size() < maximum }.collect { chunk ->
            require(chunk.sampleRate == format.sampleRate && chunk.channels == format.channels)
            bytes.write(chunk.bytes, 0, minOf(chunk.bytes.size, maximum - bytes.size()))
        }
        return PcmAudio(bytes.toByteArray(), format.sampleRate, format.channels)
    }
    fun speak() {
        if (state().speaking) { speech?.cancel(); return }
        val text = state().editorText
        if (text.isBlank()) { message("请先输入文本"); return }
        val engine = registry.plugin(selection(true).orEmpty())?.tts ?: registry.plugins.firstNotNullOfOrNull { it.tts }
        if (engine == null) { message("请先安装并选择语音合成插件"); return }
        if (engine.info.availability != EngineAvailability.READY) { message("语音合成插件尚未配置资源"); return }
        speech = scope.launch {
            update { it.copy(speaking = true) }
            val player = audio.player()
            try {
                var offset = 0
                val limit = engine.info.maxTextLength.coerceAtLeast(2)
                while (offset < text.length) {
                    var end = minOf(offset + limit, text.length)
                    if (end < text.length && text[end - 1].isHighSurrogate()) end--
                    engine.synthesize(TtsRequest(text.substring(offset, end))).collect { event ->
                        if (event is SynthesisEvent.Audio) player.write(event.audio)
                    }
                    offset = end
                }
                player.finish()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "朗读失败") }
            finally { player.close(); update { it.copy(speaking = false) } }
        }
    }
    suspend fun stopAll() {
        pending = null; microphoneGeneration++
        val jobs = listOfNotNull(recognition, speech, voice)
        jobs.forEach { it.cancel() }; jobs.joinAll()
        update { it.copy(listening = false, speaking = false) }
    }
}
