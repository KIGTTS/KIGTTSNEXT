// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

/** Uniform facade owns routing and fallback, while providers own inference and voice files. */
internal class ServiceSynthesis(private val settings: ServiceSettings, private val broker: SpeechBroker,
    private val system: TtsEngine) : TtsEngine {
    private val routing = ServiceRouting(settings, broker, system)
    @Volatile var cache: ServiceCache? = null
    override val info get() = TtsInfo(role = TtsRole.SERVICE, selectedVoiceId = if (settings.provider == null) null else settings.voice, supportsControl = true)
    fun settingsChanged(automaticRead: Boolean? = null, speechActive: Boolean? = null) = routing.changed(automaticRead, speechActive)
    override suspend fun control(command: TtsControl) { routing.control(command) }
    override suspend fun close() { cache?.close(); routing.close() }
    override fun synthesize(request: TtsRequest) = synthesize(request, null)
    fun synthesize(request: TtsRequest, phraseId: String?) = flow {
        val localCache = cache
        if (localCache != null) localCache.foreground(request, phraseId) { emit(it) }
        else generate(request.copy(rate = (request.rate * settings.rate).coerceIn(.25f, 4f), profile = TtsProfile.REALTIME), null, true) { emit(it) }
    }.flowOn(Dispatchers.IO)

    /** Returns false when the output is not from the requested voice. Never bake fallbacks. */
    suspend fun generate(request: TtsRequest, expected: CacheVoice?, allowFallback: Boolean = false, profileVersion: String? = null,
        emit: suspend (SynthesisEvent) -> Unit): Boolean = routing.use { provider ->
        generateUsing(provider, request, expected, allowFallback, profileVersion, emit)
    }

    private suspend fun generateUsing(provider: TtsProvider?, request: TtsRequest, expected: CacheVoice?, allowFallback: Boolean,
        profileVersion: String?, emit: suspend (SynthesisEvent) -> Unit): Boolean {
        val voice = settings.voice ?: provider?.info?.selectedVoiceId
        val matchesVoice = expected == null || if (provider == null) expected.owner == SYSTEM_PROVIDER && settings.provider == null
            else expected.owner == provider.pluginId && expected.voice == voice
        val profiles = provider?.info?.profiles
        val actualProfileVersion = if (request.profile == TtsProfile.PREBAKE) profiles?.prebake else profiles?.realtime
        val matches = matchesVoice && (profileVersion == null || profileVersion == actualProfileVersion)
        if (!matches && !allowFallback) return false
        if (provider == null) {
            routing.fallback()
            generateSystem(request, emit)
            return matches
        }
        emit(SynthesisEvent.Started)
        var offset = 0
        val limit = provider.info.maxTextLength
        while (offset < request.text.length) {
            currentCoroutineContext().ensureActive()
            val end = segmentEnd(request.text, offset, limit)
            var audioSent = false
            try {
                broker.synthesize(provider.pluginId, request.copy(text = request.text.substring(offset, end),
                    voiceId = voice, profile = if (profiles != null) request.profile else TtsProfile.REALTIME)).collect { event ->
                    if (event is SynthesisEvent.Audio && event.audio.bytes.isNotEmpty()) { audioSent = true; emit(event) }
                }
                if (!audioSent && request.text.substring(offset, end).isNotBlank())
                    throw EngineException(EngineError.INFERENCE, "引擎未输出音频")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (audioSent || !allowFallback) throw error
                routing.fallback()
                generateSystem(request.copy(text = request.text.substring(offset)), { emit(it) }, started = true)
                return false
            }
            offset = end
        }
        emit(SynthesisEvent.Finished())
        if (!matches || profileVersion == null) return matches
        // Avoid committing audio under stale settings if a provider was reconfigured
        // while a multi-segment request was being produced.
        val latest = runCatching { broker.providers().firstOrNull { it.pluginId == provider.pluginId }?.info?.profiles }.getOrNull()
        return (if (request.profile == TtsProfile.PREBAKE) latest?.prebake else latest?.realtime) == profileVersion
    }

    private suspend fun generateSystem(request: TtsRequest, emit: suspend (SynthesisEvent) -> Unit, started: Boolean = false) {
        val limit = system.info.maxTextLength
        if (!started) emit(SynthesisEvent.Started)
        var offset = 0
        var notice: String? = null
        var audioSent = false
        while (offset < request.text.length) {
            val end = segmentEnd(request.text, offset, limit)
            flow { emitAll(system.synthesize(request.copy(text = request.text.substring(offset, end), voiceId = null))) }
                .catch { error ->
                    if (error is CancellationException || audioSent) throw error
                    throw SystemReadingUnavailable(error)
                }.collect {
                    when (it) {
                        is SynthesisEvent.Audio -> { audioSent = audioSent || it.audio.bytes.isNotEmpty(); emit(it) }
                        is SynthesisEvent.Finished -> notice = it.notice ?: notice
                        else -> Unit
                    }
                }
            offset = end
        }
        if (!audioSent && request.text.isNotBlank()) throw SystemReadingUnavailable()
        emit(SynthesisEvent.Finished(notice))
    }

    private fun segmentEnd(text: String, offset: Int, limit: Int): Int {
        val end = minOf(offset + limit, text.length)
        return if (end < text.length && text[end - 1].isHighSurrogate()) end - 1 else end
    }
}

class SystemReadingUnavailable(cause: Throwable? = null) : Exception("System speech synthesis is unavailable", cause)
