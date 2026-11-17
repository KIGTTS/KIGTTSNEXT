package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ServiceSynthesisTest {
    @get:Rule val files = TemporaryFolder()
    private val pcm = SynthesisEvent.Audio(PcmAudio(byteArrayOf(1, 0)))
    private class Broker : SpeechBroker {
        @Volatile var catalog = listOf(TtsProvider("test.provider", "test", TtsInfo(maxTextLength = 3)))
        var catalogError = false
        val requests = CopyOnWriteArrayList<TtsRequest>()
        var output: suspend FlowCollector<SynthesisEvent>.(TtsRequest) -> Unit = { emit(SynthesisEvent.Audio(PcmAudio(byteArrayOf(2, 0)))) }
        override fun providers(): List<TtsProvider> { if (catalogError) error("broker disconnected"); return catalog }
        override suspend fun command(pluginId: String, command: VoiceCommand) = PluginOutcome.RefreshUi
        override fun synthesize(pluginId: String, request: TtsRequest) = flow {
            requests += request; emit(SynthesisEvent.Started); output(request); emit(SynthesisEvent.Finished())
        }
    }
    private inner class System : TtsEngine {
        val requests = mutableListOf<TtsRequest>()
        override fun synthesize(request: TtsRequest) = flow {
            requests += request; emit(SynthesisEvent.Started); emit(pcm); emit(SynthesisEvent.Finished())
        }
    }
    private fun settings() = ServiceSettings(PluginStorage(files.newFolder())).also { it.provider = "test.provider"; it.voice = "chosen" }
    @Test fun backgroundProgressCountsCompletedEntriesAndClearsWhenFinished() = runBlocking {
        val broker = profiledBroker()
        val second = CompletableDeferred<Unit>()
        broker.output = { request ->
            if (request.text == "乙") second.await()
            emit(SynthesisEvent.Audio(PcmAudio(byteArrayOf(2, 0))))
        }
        val options = settings(); val facade = ServiceSynthesis(options, broker, System())
        val cache = ServiceCache(files.newFolder(), options, broker, MutableStateFlow(linkedMapOf("a" to "甲", "b" to "乙")),
            { mapOf("test.provider" to "1") }, { "system" }, facade, MutableStateFlow(0L))
        facade.cache = cache
        try {
            withTimeout(10_000) { cache.bakeProgress.first { it == .5f } }
            second.complete(Unit)
            withTimeout(10_000) { cache.bakeProgress.first { it == null } }
            assertEquals(listOf("甲", "乙"), broker.requests.map { it.text })
        } finally { facade.close() }
    }
    @Test fun shortcutAndLiveProfilesDoNotShareAudioOrInvalidateEachOther() = runBlocking {
        val broker = Broker().also { it.catalog = listOf(TtsProvider("test.provider", "test",
            TtsInfo(voices = listOf(VoiceInfo("chosen", "Voice", "zh-CN")), profiles = TtsProfileVersions("fast-v1", "quality-v1")))) }
        fun updateProfiles(realtime: String, prebake: String) {
            broker.catalog = broker.catalog.map { it.copy(info = it.info.copy(profiles = TtsProfileVersions(realtime, prebake))) }
        }
        broker.output = { request ->
            emit(SynthesisEvent.Audio(PcmAudio(byteArrayOf(if (request.profile == TtsProfile.PREBAKE) 4 else 2, 0))))
        }
        val options = settings()
        val facade = ServiceSynthesis(options, broker, System())
        val cacheDir = files.newFolder()
        val cache = ServiceCache(cacheDir, options, broker, MutableStateFlow(mapOf("phrase" to "你好")),
            { mapOf("test.provider" to "1") }, { "system" }, facade, MutableStateFlow(0L))
        facade.cache = cache
        suspend fun speak(id: String? = null) = facade.synthesize(TtsRequest("你好"), id)
            .filterIsInstance<SynthesisEvent.Audio>().single().audio.bytes.first().toInt()
        suspend fun waitFor(predicate: () -> Boolean) = withTimeout(10_000) { while (!predicate()) delay(10) }
        fun baked() = cacheDir.walkTopDown().firstOrNull { it.isFile && it.name.startsWith("q_") }?.name
        try {
            assertEquals(2, speak())
            waitFor { baked() != null }
            assertEquals(4, speak("phrase"))
            assertEquals(2, speak())
            assertEquals(listOf(TtsProfile.REALTIME, TtsProfile.PREBAKE), broker.requests.map { it.profile })
            updateProfiles("fast-v2", "quality-v1"); cache.changed()
            assertEquals(4, speak("phrase"))
            assertEquals(2, speak())
            assertEquals(listOf(TtsProfile.REALTIME, TtsProfile.PREBAKE, TtsProfile.REALTIME), broker.requests.map { it.profile })
            val oldBake = baked()
            updateProfiles("fast-v2", "quality-v2")
            // New baked key is not ready: return live audio without poisoning it.
            assertEquals(2, speak("phrase"))
            waitFor { baked()?.let { it != oldBake } == true }
            assertEquals(4, speak("phrase"))
            val liveCount = broker.requests.count { it.profile == TtsProfile.REALTIME }
            assertEquals("已安排重新缓存当前声音的快捷文本", cache.rebuild())
            assertNull(baked())
            assertEquals(2, speak())
            assertEquals(liveCount, broker.requests.count { it.profile == TtsProfile.REALTIME })
            waitFor { baked() != null }
            assertEquals(3, broker.requests.count { it.profile == TtsProfile.PREBAKE })
            assertEquals(4, speak("phrase"))
        } finally { facade.close() }
    }
    @Test fun realtimePreemptsLocalBakeAndProvisionalPlaybackDoesNotInterruptOtherEntries() = runBlocking {
        val broker = profiledBroker()
        val interrupted = CompletableDeferred<Unit>()
        val initial = CompletableDeferred<Unit>()
        val other = CompletableDeferred<Unit>()
        val releaseOther = CompletableDeferred<Unit>()
        broker.output = { request ->
            if (request.profile == TtsProfile.PREBAKE && request.text == "甲" && initial.complete(Unit)) {
                try { awaitCancellation() } finally { interrupted.complete(Unit) }
            }
            if (request.profile == TtsProfile.PREBAKE && request.text == "乙") {
                other.complete(Unit)
                releaseOther.await()
            }
            emit(SynthesisEvent.Audio(PcmAudio(byteArrayOf(if (request.profile == TtsProfile.PREBAKE) 4 else 2, 0))))
        }
        val options = settings()
        val facade = ServiceSynthesis(options, broker, System())
        val cacheDir = files.newFolder()
        facade.cache = ServiceCache(cacheDir, options, broker, MutableStateFlow(linkedMapOf("a" to "甲", "b" to "乙")),
            { mapOf("test.provider" to "1") }, { "system" }, facade, MutableStateFlow(0L))
        suspend fun speak() = facade.synthesize(TtsRequest("甲"), "a").filterIsInstance<SynthesisEvent.Audio>().single().audio.bytes[0].toInt()
        try {
            withTimeout(10_000) {
                initial.await()
                assertEquals(2, speak())
                assertTrue(interrupted.isCompleted)
                other.await()
                assertEquals(2, speak())
                assertEquals(1, broker.requests.count { it.profile == TtsProfile.REALTIME })
                assertEquals(1, broker.requests.count { it.profile == TtsProfile.PREBAKE && it.text == "甲" })
                releaseOther.complete(Unit)
                while (cacheDir.walkTopDown().count { it.name.startsWith("q_") } < 2) delay(10)
                assertEquals(4, speak())
                assertEquals(listOf("甲", "乙", "甲"), broker.requests.filter { it.profile == TtsProfile.PREBAKE }.map { it.text })
            }
        } finally { facade.close() }
    }

    @Test fun concurrentEngineCanFinishRealtimeWhileHighQualityIsStillInFlight() = runBlocking {
        val broker = profiledBroker()
        val baking = CompletableDeferred<Unit>()
        val releaseBake = CompletableDeferred<Unit>()
        broker.output = { request ->
            if (request.profile == TtsProfile.PREBAKE) { baking.complete(Unit); releaseBake.await() }
            emit(pcm)
        }
        val options = settings()
        val facade = ServiceSynthesis(options, broker, System())
        facade.cache = ServiceCache(files.newFolder(), options, broker, MutableStateFlow(mapOf("a" to "甲")),
            { mapOf("test.provider" to "1") }, { "system" }, facade, MutableStateFlow(0L), parallelProvider = { true })
        try {
            withTimeout(10_000) {
                baking.await()
                val events = facade.synthesize(TtsRequest("实时")).toList()
                assertEquals(1, events.count { it is SynthesisEvent.Audio })
                assertFalse(releaseBake.isCompleted)
                assertEquals(listOf(TtsProfile.PREBAKE, TtsProfile.REALTIME), broker.requests.map { it.profile })
            }
        } finally { releaseBake.complete(Unit); facade.close() }
    }

    private fun profiledBroker() = Broker().also { it.catalog = listOf(TtsProvider("test.provider", "test",
        TtsInfo(voices = listOf(VoiceInfo("chosen", "Voice", "zh-CN")),
            profiles = TtsProfileVersions("fast-v1", "quality-v1")))) }
    @Test fun splitsProviderRequestsWithoutSplittingSurrogatesAndEmitsSingleLifecycle() = runBlocking {
        val broker = Broker(); val system = System()
        val options = settings().also { it.rate = 1.5f }
        val events = ServiceSynthesis(options, broker, system).synthesize(TtsRequest("ab😀cd", rate = 2f)).toList()
        assertEquals(listOf("ab", "😀c", "d"), broker.requests.map { it.text })
        assertTrue(broker.requests.all { it.voiceId == "chosen" && it.rate == 3f })
        assertEquals(1, events.count { it is SynthesisEvent.Started })
        assertEquals(1, events.count { it is SynthesisEvent.Finished })
        assertTrue(system.requests.isEmpty())
    }
    @Test fun providerMissingUnconfiguredOrBrokerDisconnectedUsesBuiltInSystem() = runBlocking {
        for (state in 0..2) {
            val broker = Broker().also { when (state) {
                0 -> it.catalog = emptyList()
                1 -> it.catalog = listOf(TtsProvider("test.provider", "test", TtsInfo(EngineAvailability.NEEDS_RESOURCES)))
                else -> it.catalogError = true
            } }
            val system = System()
            val options = settings().also { it.systemVoice = "system.voice" }
            ServiceSynthesis(options, broker, system).synthesize(TtsRequest("你好")).toList()
            assertNull(system.requests.single().voiceId)
            assertTrue(broker.requests.isEmpty())
        }
    }
    @Test fun failureBeforeAudioFallsBackOnlyForRemainingText() = runBlocking {
        val broker = Broker().also { it.output = { if (it.text == "def") error("inference failed") else emit(pcm) } }
        val system = System()
        val events = ServiceSynthesis(settings(), broker, system).synthesize(TtsRequest("abcdefghi")).toList()
        assertEquals(listOf("abc", "def"), broker.requests.map { it.text })
        assertEquals("defghi", system.requests.single().text)
        assertEquals(1, events.count { it is SynthesisEvent.Started })
        assertEquals(1, events.count { it is SynthesisEvent.Finished })
    }
    @Test fun failureAfterAudioNeverReplaysFromSystem() = runBlocking {
        val broker = Broker().also { it.output = { emit(pcm); error("failure after audio") } }; val system = System()
        val result = runCatching { ServiceSynthesis(settings(), broker, system).synthesize(TtsRequest("abc")).toList() }
        assertTrue(result.isFailure)
        assertTrue(system.requests.isEmpty())
    }
    @Test fun silentProviderFallsBack() = runBlocking {
        val broker = Broker().also { it.output = {} }; val system = System()
        ServiceSynthesis(settings(), broker, system).synthesize(TtsRequest("abc")).toList()
        assertEquals("abc", system.requests.single().text)
    }
    @Test fun cancellationReachesProviderAndDoesNotTriggerFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val released = CompletableDeferred<Unit>()
        val broker = Broker().also { it.output = { entered.complete(Unit); try { awaitCancellation() } finally { released.complete(Unit) } } }
        val system = System()
        val task = launch { ServiceSynthesis(settings(), broker, system).synthesize(TtsRequest("abc")).collect() }
        withTimeout(5000) { entered.await() }; task.cancelAndJoin()
        assertTrue(released.isCompleted)
        assertTrue(system.requests.isEmpty())
    }
    @Test fun settingsPersistEngineVoiceAndSystemFallbackConfiguration() {
        val storage = PluginStorage(files.newFolder())
        ServiceSettings(storage).apply {
            assertTrue(cacheEnabled); cacheEnabled = false
            provider = "test.provider"; voice = "v"; systemVoice = "s"; rate = 1.5f; language = "en-US"; save()
        }
        val restored = ServiceSettings(storage)
        assertEquals("test.provider", restored.provider); assertEquals("v", restored.voice)
        assertEquals("s", restored.systemVoice); assertEquals("en-US", restored.language); assertEquals(1.5f, restored.rate)
        assertFalse(restored.cacheEnabled)
    }
    @Test fun onlySelectedPackBakesAndLateLoadingNeverCachesFallback() = runBlocking {
        val options = settings().also { it.voice = "a" }
        val broker = Broker().also { it.catalog = emptyList() }
        val system = System()
        val phrases = MutableStateFlow(linkedMapOf("fixed" to "甲", "second" to "乙") as Map<String, String>)
        val facade = ServiceSynthesis(options, broker, system)
        val cache = ServiceCache(files.newFolder(), options, broker, phrases, { mapOf("test.provider" to "1") },
            { "system-v1" }, facade, MutableStateFlow(0L))
        facade.cache = cache
        fun catalog(value: Float = 1f) = listOf(TtsProvider("test.provider", "test", TtsInfo(managesVoices = true,
            voices = listOf(VoiceInfo("a", "A", "zh-CN", packId = "pack-a"), VoiceInfo("b", "B", "zh-CN", packId = "pack-b")),
            packs = listOf(VoicePackInfo("pack-a", VoicePackMetadata("A"), listOf("a"),
                parameters = listOf(VoiceParameter("tempo", "Tempo", value, .5f, 2f, 1f))),
                VoicePackInfo("pack-b", VoicePackMetadata("B"), listOf("b"))))))
        suspend fun waitFor(check: () -> Boolean) = withTimeout(10_000) { while (!check()) delay(10) }
        try {
            facade.synthesize(TtsRequest("甲"), "fixed").toList()
            assertEquals(0L, cache.bytes.value)
            assertEquals(listOf("甲"), system.requests.map { it.text })
            broker.catalog = catalog(); cache.changed()
            waitFor { broker.requests.size == 2 && cache.bytes.value > 0 }
            facade.synthesize(TtsRequest("甲"), "fixed").toList()
            assertEquals(listOf("a", "a"), broker.requests.map { it.voiceId })
            broker.catalog = emptyList(); cache.changed()
            val kept = cache.bytes.value
            facade.synthesize(TtsRequest("甲"), "fixed").toList()
            assertEquals(kept, cache.bytes.value)
            broker.catalog = catalog(); options.voice = "b"; cache.changed()
            waitFor { broker.requests.size == 4 }
            options.voice = "a"; broker.catalog = catalog(1.5f); cache.changed()
            waitFor { broker.requests.size == 6 }
            assertEquals(listOf("a", "a", "b", "b", "a", "a"), broker.requests.map { it.voiceId })
            cache.clear()
            delay(650)
            assertEquals(0L, cache.bytes.value)
        } finally { facade.close() }
    }
}
