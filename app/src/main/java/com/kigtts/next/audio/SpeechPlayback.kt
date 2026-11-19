package com.kigtts.next.audio

import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

/** Finalized sentences use the selected synthesis service in order, with a bounded queue. */
internal class SpeechPlayback(private val scope: CoroutineScope, private val registry: PluginRegistry,
    private val audio: AudioPlatform, private val state: () -> AppState,
    private val update: ((AppState) -> AppState) -> Unit, private val message: (String) -> Unit,
    private val copy: (String, String) -> String, private val selection: () -> String?) {
    private var current: Job? = null
    private var queued: Job? = null
    private val pending = ArrayDeque<String>()
    private var queuedCharacters = 0
    private var warned = false
    private var generation = 0L
    fun speak(replace: Boolean) {
        queued?.cancel(); pending.clear(); queuedCharacters = 0; warned = false
        if (state().speaking && !replace) { current?.cancel(); return }
        val text = state().editorText
        if (text.isBlank()) { message(copy("reading.input_required", "请先输入文本")); return }
        play(text, state().spokenPhraseId)
    }
    fun readOnce(text: String) {
        if (text.isBlank()) return
        queued?.cancel(); pending.clear(); queuedCharacters = 0; warned = false
        play(text)
    }
    fun enqueue(text: String) {
        if (!state().autoPlayback || text.isBlank()) return
        if (pending.size >= 64 || queuedCharacters + text.length > 32_000) {
            if (!warned) { message(copy("reading.backlog", "朗读暂时跟不上识别，部分新句已跳过")); warned = true }
            return
        }
        pending.addLast(text); queuedCharacters += text.length
        if (queued?.isActive == true) return
        queued = scope.launch {
            while (pending.isNotEmpty() && state().autoPlayback) {
                current?.join()
                if (!state().autoPlayback) break
                val next = pending.removeFirst(); queuedCharacters -= next.length
                play(next)?.join()
            }
            pending.clear(); queuedCharacters = 0; warned = false
        }
    }
    private fun play(text: String, phraseId: String? = null): Job? {
        val plugin = registry.plugin(selection().orEmpty())?.takeIf { it.tts != null } ?: registry.plugins.firstOrNull { it.tts != null }
        val engine = plugin?.tts
        if (engine == null) { message(copy("reading.engine_missing", "请在朗读设置中选择引擎和声音")); return null }
        if (engine.info.availability != EngineAvailability.READY) { message(copy("reading.resources_missing", "请在朗读设置中配置当前引擎")); return null }
        val previous = current; previous?.cancel(); val epoch = ++generation
        return scope.launch {
            previous?.join()
            update { it.copy(speaking = true) }
            val player = audio.player()
            try {
                if (plugin is com.kigtts.engines.speechservice.SpeechServicePlugin) {
                    plugin.synthesizeText(TtsRequest(text), phraseId).collect { event -> if (event is SynthesisEvent.Audio) player.write(event.audio) }
                } else {
                    var offset = 0; val limit = engine.info.maxTextLength.coerceAtLeast(2)
                    while (offset < text.length) {
                        var end = minOf(offset + limit, text.length)
                        if (end < text.length && text[end - 1].isHighSurrogate()) end--
                        engine.synthesize(TtsRequest(text.substring(offset, end))).collect { event -> if (event is SynthesisEvent.Audio) player.write(event.audio) }
                        offset = end
                    }
                }
                player.finish()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                message(if (error is com.kigtts.engines.speechservice.SystemReadingUnavailable)
                    copy("reading.system_unavailable", "系统朗读不可用，请检查系统语音设置")
                else when ((error as? EngineException)?.code) {
                    EngineError.NOT_CONFIGURED -> copy("reading.resources_missing", "请在朗读设置中配置当前引擎")
                    EngineError.TIMEOUT -> copy("reading.timeout", "朗读超时，请重试")
                    else -> copy("reading.failed", "朗读失败，请重试")
                })
            }
            finally { player.close(); if (epoch == generation) update { it.copy(speaking = false) } }
        }.also { current = it }
    }
    suspend fun stop() {
        pending.clear(); queuedCharacters = 0; warned = false
        val jobs = listOfNotNull(current, queued); jobs.forEach { it.cancel() }; jobs.joinAll()
        update { it.copy(speaking = false) }
    }
}
