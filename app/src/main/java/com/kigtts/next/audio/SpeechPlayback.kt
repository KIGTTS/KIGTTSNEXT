package com.kigtts.next.audio

import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

/** Finalized sentences use the selected synthesis service in order, with a bounded queue. */
internal class SpeechPlayback(private val scope: CoroutineScope, private val registry: PluginRegistry,
    private val audio: AudioPlatform, private val state: () -> AppState,
    private val update: ((AppState) -> AppState) -> Unit, private val message: (String) -> Unit,
    private val selection: () -> String?) {
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
        if (text.isBlank()) { message("请先输入文本"); return }
        play(text)
    }
    fun readOnce(text: String) {
        if (text.isBlank()) return
        queued?.cancel(); pending.clear(); queuedCharacters = 0; warned = false
        play(text)
    }
    fun enqueue(text: String) {
        if (!state().autoPlayback || text.isBlank()) return
        if (pending.size >= 64 || queuedCharacters + text.length > 32_000) {
            if (!warned) { message("朗读暂时跟不上识别，部分新句已跳过"); warned = true }
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
    private fun play(text: String): Job? {
        val engine = registry.plugin(selection().orEmpty())?.tts ?: registry.plugins.firstNotNullOfOrNull { it.tts }
        if (engine == null) { message("请先安装并选择语音合成插件"); return null }
        if (engine.info.availability != EngineAvailability.READY) { message("语音合成插件尚未配置资源"); return null }
        val previous = current; previous?.cancel(); val epoch = ++generation
        return scope.launch {
            previous?.join()
            update { it.copy(speaking = true) }
            val player = audio.player()
            try {
                var offset = 0; val limit = engine.info.maxTextLength.coerceAtLeast(2)
                while (offset < text.length) {
                    var end = minOf(offset + limit, text.length)
                    if (end < text.length && text[end - 1].isHighSurrogate()) end--
                    engine.synthesize(TtsRequest(text.substring(offset, end))).collect { event -> if (event is SynthesisEvent.Audio) player.write(event.audio) }
                    offset = end
                }
                player.finish()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "朗读失败") }
            finally { player.close(); if (epoch == generation) update { it.copy(speaking = false) } }
        }.also { current = it }
    }
    suspend fun stop() {
        pending.clear(); queuedCharacters = 0; warned = false
        val jobs = listOfNotNull(current, queued); jobs.forEach { it.cancel() }; jobs.joinAll()
        update { it.copy(speaking = false) }
    }
}
