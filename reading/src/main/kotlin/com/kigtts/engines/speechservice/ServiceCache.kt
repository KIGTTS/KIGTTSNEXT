// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Only the selected voice is prepared; missing, disabled and still-loading engines are not deletions. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ServiceCache(directory: File, private val settings: ServiceSettings, private val broker: SpeechBroker,
    phrases: Flow<Map<String, String>>, private val installed: () -> Map<String, String>?,
    private val systemRevision: () -> String, private val synthesis: ServiceSynthesis, size: MutableStateFlow<Long>,
    private val mayCacheProvider: (String) -> Boolean = { true }) {
    private val store = SpeechAudioCache(directory, size)
    val bytes get() = store.bytes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val gate = Mutex()
    private val playing = AtomicInteger()
    @Volatile private var shortcuts = emptyMap<String, String>()
    @Volatile private var installedRevisions = emptyMap<String, String>()
    @Volatile private var lastPlan: Plan? = null
    @Volatile private var clearedPlan: Plan? = null
    private val revision = AtomicInteger()
    private data class Plan(val voice: CacheVoice, val rate: Float, val phrases: Map<String, String>, val revision: Int,
        val prebakeVersion: String?)
    private data class CatalogSnapshot(val owners: Map<String, String>?, val providers: Map<String, TtsInfo>)
    private var reconciled: CatalogSnapshot? = null
    private var reconciledVoice: CacheVoice? = null
    private fun catalog() = runCatching { broker.providers() }.getOrNull()

    init {
        scope.launch {
            phrases.collect { items ->
                if (items != shortcuts) {
                    shortcuts = items
                    store.updatePhrases(items)
                    changed()
                } else store.updatePhrases(items)
            }
        }
        scope.launch {
            merge(signal.receiveAsFlow(), flow { while (true) { emit(Unit); delay(5_000) } })
                .map { plan() }.distinctUntilChanged().collectLatest { plan ->
                    if (plan != null) {
                        delay(500)
                        while (!prepare(plan)) delay(60_000)
                    }
                }
        }
    }
    fun changed() { signal.trySend(Unit) }
    fun optionsChanged() {
        store.suspendWrites()
        revision.incrementAndGet()
        changed()
    }
    fun forget(owner: String, packs: Set<String>) {
        if (packs.isEmpty()) return
        store.forgetPacks(owner, packs)
        optionsChanged()
    }
    fun clear() { clearedPlan = lastPlan ?: currentPlan(catalog()); store.clear(); changed() }
    fun rebuild(): String {
        if (!settings.cacheEnabled) return "请先开启自动缓存"
        if (shortcuts.isEmpty()) return "没有可预合成的快捷文本"
        val plan = currentPlan(catalog()) ?: return "当前声音还不能预合成，请检查引擎和权限"
        revision.incrementAndGet()
        clearedPlan = null
        store.forgetShortcuts(plan.voice)
        changed()
        return "已安排重新缓存当前声音的快捷文本"
    }
    fun close() { scope.cancel(); store.suspendWrites() }

    private fun voice(provider: TtsProvider, id: String?): CacheVoice? {
        val info = provider.info
        val selected = id ?: info.selectedVoiceId
        if (info.packs.isEmpty() && info.voices.isEmpty() && (selected != null || info.importsVoicePacks || info.managesVoices))
            return null
        val infoVoice = info.voices.firstOrNull { it.id == selected }
        if (selected != null && (info.voices.isNotEmpty() || info.packs.isNotEmpty()) &&
            infoVoice == null && info.packs.none { selected in it.voiceIds }) return null
        val pack = info.packs.firstOrNull { selected in it.voiceIds }
        val packId = pack?.id ?: infoVoice?.packId ?: selected ?: DEFAULT_PACK
        return providerVoice(provider.pluginId, packId, pack, selected)
    }
    private fun providerVoice(owner: String, packId: String, pack: VoicePackInfo?, selected: String?): CacheVoice =
        CacheVoice(owner, packId, cacheHash(installedRevisions[owner], pack?.voiceIds?.sorted(),
            pack?.parameters?.sortedBy { it.id }?.map { it.id to it.value }), selected)
    private fun systemVoice() = CacheVoice(SYSTEM_PROVIDER, DEFAULT_PACK, cacheHash(systemRevision()), null)
    private fun currentPlan(catalog: List<TtsProvider>?): Plan? {
        val owner = settings.provider
        val provider = selectedProvider(catalog, owner)
        val voice = if (owner == null) systemVoice() else provider?.takeIf { mayCacheProvider(owner) }
            ?.let { voice(it, settings.voice) } ?: return null
        return Plan(voice, settings.rate, shortcuts, revision.get(), provider?.info?.profiles?.prebake)
    }
    private fun selectedProvider(catalog: List<TtsProvider>?, owner: String?) = catalog?.firstOrNull {
        it.pluginId == owner && it.enabled && it.info.availability == EngineAvailability.READY
    }
    private fun plan(): Plan? {
        if (playing.get() != 0) return null
        if (!settings.cacheEnabled && bytes.value == 0L) return null
        val inventory = runCatching { installed() }.getOrNull()
        if (inventory != null) installedRevisions = inventory
        val catalog = catalog()
        val ready = catalog.orEmpty().filter { it.enabled && it.info.availability == EngineAvailability.READY }
        val plan = currentPlan(catalog)
        val snapshot = CatalogSnapshot(inventory, ready.associate { it.pluginId to it.info })
        if (snapshot != reconciled || plan?.voice != reconciledVoice) {
            reconcile(inventory, ready, plan)
            reconciled = snapshot
            reconciledVoice = plan?.voice
        }
        lastPlan = plan
        if (clearedPlan != null && plan != null && clearedPlan != plan) clearedPlan = null
        return plan?.takeIf { settings.cacheEnabled && it != clearedPlan && it.phrases.isNotEmpty() }
    }
    private fun reconcile(inventory: Map<String, String>?, ready: List<TtsProvider>, plan: Plan?) {
        // Even a READY engine can list only loaded/configured packs. Only confirmed deletions
        // or the host's installed-package inventory are allowed to remove missing packs.
        val revisions = ready.flatMap { provider ->
            provider.info.packs.map { providerVoice(provider.pluginId, it.id, it, null) } +
                provider.info.voices.mapNotNull { voice(provider, it.id) }
        } + listOfNotNull(plan?.voice)
        store.reconcile(inventory?.keys?.plus(SYSTEM_PROVIDER), revisions)
    }
    private suspend fun prepare(plan: Plan): Boolean {
        var retry = false
        for ((id, text) in plan.phrases) {
            if (playing.get() != 0 || !settings.cacheEnabled) return true
            val request = TtsRequest(text, rate = plan.rate,
                profile = if (plan.prebakeVersion != null) TtsProfile.PREBAKE else TtsProfile.REALTIME)
            val key = AudioCacheKey(plan.voice, id, text, request.language, plan.rate, null, plan.prebakeVersion)
            if (store.has(key)) continue
            val complete = try {
                gate.withLock {
                    if (playing.get() != 0 || plan != lastPlan || clearedPlan == plan || plan.revision != revision.get()) return true
                    val writer = store.writer(key) ?: return true
                    writer.use {
                        val valid = withTimeoutOrNull(if (plan.prebakeVersion != null) 120_000 else 30_000) {
                            synthesis.generate(request, plan.voice, profileVersion = plan.prebakeVersion) { event ->
                                if (event is SynthesisEvent.Audio) writer.append(event.audio)
                            }
                        } == true
                        currentCoroutineContext().ensureActive()
                        valid && clearedPlan != plan && plan.revision == revision.get() && writer.finish()
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { false }
            if (!complete) retry = true
            yield()
        }
        return !retry
    }

    suspend fun foreground(request: TtsRequest, phraseId: String?, emit: suspend (SynthesisEvent) -> Unit) {
        playing.incrementAndGet(); changed()
        try {
            gate.withLock {
                val rated = request.copy(rate = (request.rate * settings.rate).coerceIn(.25f, 4f), profile = TtsProfile.REALTIME)
                val owner = settings.provider
                val provider = if (owner != null) selectedProvider(catalog(), owner) else null
                val voice = if (owner == null) systemVoice() else provider?.let { voice(it, settings.voice) }
                if (voice == null || !settings.cacheEnabled || request.text.isBlank() ||
                    (voice.owner != SYSTEM_PROVIDER && !mayCacheProvider(voice.owner))) {
                    synthesis.generate(rated, voice, allowFallback = true, emit = emit)
                    return@withLock
                }
                val quickId = phraseId?.takeIf { shortcuts[it] == request.text }
                val profiles = provider?.info?.profiles
                val key = AudioCacheKey(voice, quickId, request.text, request.language, rated.rate, request.voiceId,
                    if (quickId == null) profiles?.realtime else profiles?.prebake)
                store.open(key)?.let { reader ->
                    var began = false
                    try {
                        reader.use {
                            while (true) {
                                val audio = reader.next() ?: break
                                if (!began) { emit(SynthesisEvent.Started); began = true }
                                emit(SynthesisEvent.Audio(audio))
                            }
                        }
                        if (began) { emit(SynthesisEvent.Finished()); return@withLock }
                    } catch (error: IOException) { store.discard(key); if (began) throw error }
                }
                // A quick-text miss must be responsive, but its real-time audio cannot replace
                // the high-quality result that the pre-bake queue will produce.
                if (quickId != null && profiles != null) {
                    synthesis.generate(rated, voice, allowFallback = true, emit = emit)
                    return@withLock
                }
                store.writer(key).use { writer ->
                    val valid = synthesis.generate(rated, voice, allowFallback = true, profileVersion = profiles?.realtime) { event ->
                        if (event is SynthesisEvent.Audio) writer?.append(event.audio)
                        emit(event)
                    }
                    currentCoroutineContext().ensureActive()
                    if (valid && settings.cacheEnabled) writer?.finish()
                }
            }
        } finally { playing.decrementAndGet(); changed() }
    }

    private companion object { const val DEFAULT_PACK = "default" }
}
