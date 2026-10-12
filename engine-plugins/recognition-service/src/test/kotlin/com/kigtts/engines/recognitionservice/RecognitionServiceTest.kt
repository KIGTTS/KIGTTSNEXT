package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecognitionServiceTest {
    private lateinit var root: File
    @Before fun setup() { root = Files.createTempDirectory("recognition-service-test").toFile() }
    @After fun cleanup() { root.deleteRecursively() }
    private class Broker : RecognitionBroker {
        var catalog = listOf(AsrProvider("test.segmented", "离线分段", AsrInfo(streaming = StreamingKind.SEGMENTED)),
            AsrProvider("test.online", "在线", AsrInfo(), settingsPages = listOf(AsrSettingsPage("packs", "识别包"))))
        val calls = mutableListOf<String>(); var cancelled = false; var hold = false
        override fun providers() = catalog
        override suspend fun recognize(pluginId: String, audio: PcmAudio, config: RecognitionConfig): RecognitionResult {
            calls += pluginId; return RecognitionResult("$pluginId:${config.language}:${audio.bytes.size}")
        }
        override fun recognizeStream(pluginId: String, audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
            calls += pluginId
            try {
                emit(RecognitionEvent.Started)
                audio.collect { emit(RecognitionEvent.Partial("$pluginId:${it.bytes.size}")) }
                if (hold) awaitCancellation()
                emit(RecognitionEvent.Final(RecognitionResult("$pluginId:final")))
            } finally { cancelled = true }
        }
    }
    private suspend fun plugin(broker: Broker) = RecognitionServicePlugin().also { plugin ->
        plugin.attach(object : PluginEnvironment {
            override val storage = PluginStorage(root); override val assets = File(root, "assets")
            override val recognitionBroker = broker
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("Service never downloads models")
        })
    }
    @Test fun settingsPageKeepsEnableSelectionAndProviderPagesWithoutDuplicatingEngineStatus() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker)
        val page = plugin.page("engine/test.online", PluginContext("", ""))!!
        assertTrue(page.blocks.isEmpty())
        val items = page.management!!.items
        assertEquals(ManagementToggle(true, "toggle/test.online"), items.first().toggle)
        assertTrue(items[1].selected)
        assertEquals("settings/test.online/packs", items.last().primaryAction)
        assertTrue(plugin.page("manager", PluginContext("", ""))!!.blocks.all { it is PageBlock.Actions })
        broker.catalog = broker.catalog.map { if (it.pluginId == "test.online") it.copy(enabled = false) else it }
        val disabled = plugin.page("engine/test.online", PluginContext("", ""))!!.management!!.items
        assertEquals(2, disabled.size); assertFalse(disabled.first().toggle!!.checked)
    }
    @Test fun serviceForwardsIncrementalEventsAndBatchToSeparateProvider() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker)
        assertEquals(AsrRole.SERVICE, plugin.asr.info.role); assertEquals(StreamingKind.INCREMENTAL, plugin.asr.info.streaming)
        val events = plugin.asr.recognizeStream(flowOf(PcmAudio(byteArrayOf(1, 0)))).toList()
        assertEquals(listOf(RecognitionEvent.Started, RecognitionEvent.Partial("test.online:2"), RecognitionEvent.Final(RecognitionResult("test.online:final"))), events)
        assertEquals("test.online:en:2", plugin.asr.recognize(PcmAudio(byteArrayOf(1, 0)), RecognitionConfig("en")).text)
        assertEquals(listOf("test.online", "test.online"), broker.calls)
    }
    @Test fun explicitChoiceSurvivesRestartAndNeverFallsBackWhenDisabled() = runBlocking<Unit> {
        val broker = Broker(); val plugin = plugin(broker)
        assertEquals(PluginOutcome.RecognitionRoutingChanged, plugin.execute("select/test.segmented", PluginContext("", "")))
        assertEquals(StreamingKind.SEGMENTED, plugin.asr.info.streaming)
        val reopened = plugin(broker); assertEquals(StreamingKind.SEGMENTED, reopened.asr.info.streaming)
        broker.catalog = broker.catalog.map { if (it.pluginId == "test.segmented") it.copy(enabled = false) else it }
        assertEquals(EngineAvailability.NEEDS_RESOURCES, reopened.asr.info.availability)
        try { reopened.asr.recognize(PcmAudio(byteArrayOf(1, 0))); fail("Silently switched engine") }
        catch (expected: EngineException) { assertEquals(EngineError.NOT_CONFIGURED, expected.code) }
        assertTrue(broker.calls.isEmpty())
    }
    @Test fun routingIsColdPinnedForRecordingAndCancellationReachesProvider() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker); broker.hold = true
        val flow = plugin.asr.recognizeStream(flowOf(PcmAudio(byteArrayOf(1, 0))))
        plugin.execute("select/test.segmented", PluginContext("", ""))
        withTimeout(2_000) { flow.first { it is RecognitionEvent.Partial } }
        assertEquals("test.segmented", broker.calls.single()); assertTrue(broker.cancelled)
        broker.hold = false; assertTrue(plugin.asr.recognize(PcmAudio(byteArrayOf(1, 0))).text.startsWith("test.segmented"))
    }
    @Test fun managerOrdersEnginesAndOpensTheProvidersOwnModelPage() = runBlocking<Unit> {
        val broker = Broker(); val plugin = plugin(broker); val page = plugin.page("manager", PluginContext("", ""))!!.management!!
        val ids = page.items.map { it.id }.reversed()
        plugin.execute("reorder", PluginContext("", "", inputs = mapOf("order" to PluginJson.encodeToString(ids))))
        assertEquals(ids, plugin(broker).page("manager", PluginContext("", ""))!!.management!!.items.map { it.id })
        assertEquals(PluginOutcome.OpenPluginPage("test.online", "packs"), plugin.execute("settings/test.online/packs", PluginContext("", "")))
        assertEquals(PluginOutcome.SetPluginEnabled("test.online", false), plugin.execute("toggle/test.online", PluginContext("", "")))
        assertEquals(PluginOutcome.SelectRecognitionService("service.speech-recognition"), plugin.execute("activate", PluginContext("", "")))
        assertTrue(File(root, "config/recognition-service.json").isFile); assertFalse(File(root, "models").exists())
    }
    @Test fun unavailableEnginesBlockInputAndStreamKindIsNeverUpgradedByFacade() = runBlocking<Unit> {
        val broker = Broker(); broker.catalog = listOf(AsrProvider("test.batch", "完整录音", AsrInfo(streaming = StreamingKind.NONE)))
        val plugin = plugin(broker); assertEquals(StreamingKind.NONE, plugin.asr.info.streaming)
        try { plugin.asr.recognizeStream(emptyFlow()).toList(); fail("Invented streaming capability") }
        catch (expected: EngineException) { assertEquals(EngineError.UNSUPPORTED_FORMAT, expected.code) }
        broker.catalog = emptyList(); assertEquals(EngineAvailability.NEEDS_RESOURCES, plugin.asr.info.availability)
        assertTrue(broker.calls.isEmpty())
    }
    @Test fun selectingDisabledProviderRequestsHostEnableWithoutInliningEngine() = runBlocking<Unit> {
        val broker = Broker(); broker.catalog = broker.catalog.map { it.copy(enabled = false) }; val plugin = plugin(broker)
        assertEquals(PluginOutcome.SetPluginEnabled("test.online", true), plugin.execute("select/test.online", PluginContext("", "")))
        assertEquals(EngineAvailability.NEEDS_RESOURCES, plugin.asr.info.availability)
        broker.catalog = broker.catalog.map { if (it.pluginId == "test.online") it.copy(enabled = true) else it }
        assertEquals(StreamingKind.INCREMENTAL, plugin.asr.info.streaming)
    }
}
