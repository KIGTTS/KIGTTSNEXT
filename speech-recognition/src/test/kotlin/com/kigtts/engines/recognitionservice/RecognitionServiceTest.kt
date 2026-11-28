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
    @Test fun settingsCanOpenWhileVoiceRecognitionIsStarting() {
        val service = RecognitionServicePlugin()
        val context = PluginContext("", "", locale = "en")
        val page = requireNotNull(service.page("manager", context))
        assertTrue(page.loading)
        assertTrue(page.blocks.isEmpty())
        assertEquals("Speech recognition", page.title)
    }

    private class Broker : RecognitionBroker {
        var catalog = listOf(AsrProvider("test.segmented", "离线分段", AsrInfo(streaming = StreamingKind.SEGMENTED)),
            AsrProvider("test.online", "在线", AsrInfo(), settingsPages = listOf(AsrSettingsPage("packs", "识别包"))))
        val calls = mutableListOf<String>(); var cancelled = false; var hold = false
        var events: List<RecognitionEvent>? = null
        override fun providers() = catalog
        override suspend fun recognize(pluginId: String, audio: PcmAudio, config: RecognitionConfig): RecognitionResult {
            calls += pluginId; return RecognitionResult("$pluginId:${config.language}:${audio.bytes.size}")
        }
        override fun recognizeStream(pluginId: String, audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
            calls += pluginId
            try {
                events?.let { scripted -> scripted.forEach { emit(it) }; return@flow }
                emit(RecognitionEvent.Started)
                audio.collect { emit(RecognitionEvent.Partial("$pluginId:${it.bytes.size}")) }
                if (hold) awaitCancellation()
                emit(RecognitionEvent.Final(RecognitionResult("$pluginId:final")))
            } finally { cancelled = true }
        }
    }
    private suspend fun plugin(broker: RecognitionBroker) = RecognitionServicePlugin().also { plugin ->
        plugin.attach(object : PluginEnvironment {
            override val storage = PluginStorage(root); override val assets = File(root, "assets")
            override val recognitionBroker = broker
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("Service never downloads models")
        })
    }
    @Test fun localizationUsesTheHostLanguageForTheActualCaptionsAction() = runBlocking {
        val broker = Broker()
        val online = broker.catalog.last()
        broker.catalog = broker.catalog.dropLast(1) + online.copy(settingsPages = listOf(AsrSettingsPage("packs", "ストリーミング認識パック")))
        val plugin = plugin(broker)
        try {
            val english = PluginContext("", "", locale = "en-US")
            plugin.execute("select/test.segmented", english)
            assertEquals("Captions", plugin.contributions(english).actions.first { it.id == "listen" }.label)
            assertEquals("음성 인식", plugin.page("manager", PluginContext("", "", locale = "ko-KR"))?.title)
            val japanese = PluginContext("", "", locale = "ja", recognitionServiceId = plugin.descriptor.id)
            val group = plugin.page("manager", japanese)!!.blocks.filterIsInstance<PageBlock.Group>().last()
            val entry = group.children.filterIsInstance<PageBlock.Input>().single { it.actionId.startsWith("settings/") }
            assertEquals("settings/test.online/packs", entry.actionId)
            assertEquals("ストリーミング認識パック", entry.title)
            val provider = plugin.page("engines", japanese)!!.management!!.items.first { it.id == "test.online" }
            assertEquals(entry.title, provider.title)
        } finally {
            plugin.close()
        }
    }
    @Test fun enginePickerIsSelectionOnlyAndConfigurationLivesInService() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker)
        val picker = plugin.page("engines", PluginContext("", ""))!!
        val market = (picker.blocks.single() as PageBlock.Group).children.single() as PageBlock.Input
        assertEquals("market-engines", market.actionId)
        assertEquals(PluginOutcome.OpenPluginPage("tools.package-store", "speech/recognition"),
            plugin.execute(market.actionId, PluginContext("", "")))
        assertNull(picker.management!!.reorderAction)
        assertTrue(picker.management!!.items.all { it.selection != null && it.toggle == null && it.actions.isEmpty() })
        assertEquals("disable-recognition", picker.management!!.items.first { it.selected }.primaryAction)
        assertTrue(plugin.contributions.actions.isEmpty())
        val manager = plugin.page("manager", PluginContext("", "", recognitionServiceId = plugin.descriptor.id))!!
        assertEquals("语音识别", manager.title)
        val groups = manager.blocks.filterIsInstance<PageBlock.Group>()
        assertEquals(listOf("识别", "语音输入", "加载方式", "引擎设置"), groups.map { it.title })
        assertNull(manager.management)
        assertEquals(PluginOutcome.OpenPage("engines"), plugin.execute("engines", PluginContext("", "")))
        val slider = groups.first { it.title == "加载方式" }.children.filterIsInstance<PageBlock.Slider>().single().control
        assertEquals(5f, slider.min); assertEquals(120f, slider.max)
    }

    @Test fun defaultUsesOnlyReadySystemRecognitionAndKeepsAnExplicitChoice() = runBlocking {
        val broker = Broker()
        val system = AsrProvider(SYSTEM_RECOGNITION_ID, "系统识别引擎", AsrInfo(streaming = StreamingKind.NONE))
        val external = broker.catalog
        broker.catalog = external + system.copy(info = system.info.copy(availability = EngineAvailability.NEEDS_RESOURCES))
        val plugin = plugin(broker)
        try {
            val context = PluginContext("", "")
            fun selected() = plugin.page("engines", context)!!.management!!.items.single { it.selected }.id
            assertEquals("recognition-disabled", selected())
            broker.catalog = external + system
            assertEquals(SYSTEM_RECOGNITION_ID, selected())
            plugin.execute("select/test.online", context)
            assertEquals("test.online", selected())
            plugin.execute("disable-recognition", context)
            assertEquals("recognition-disabled", selected())
        } finally { plugin.close() }
    }
    @Test fun serviceAccumulatesTranscriptAndRoutesBatchToSeparateProvider() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker)
        plugin.execute("select/test.online", PluginContext("", ""))
        assertEquals(AsrRole.SERVICE, plugin.asr.info.role); assertEquals(StreamingKind.INCREMENTAL, plugin.asr.info.streaming)
        val events = plugin.asr.recognizeStream(flowOf(PcmAudio(byteArrayOf(1, 0)))).toList()
        assertEquals(listOf(RecognitionEvent.Started,
            RecognitionEvent.Transcript("", "test.online:2", 0),
            RecognitionEvent.Transcript("test.online:final", "", 1, "test.online:final")), events)
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
        withTimeout(2_000) { flow.first { it == RecognitionEvent.Started } }
        assertEquals("test.segmented", broker.calls.single()); assertTrue(broker.cancelled)
        broker.hold = false; assertTrue(plugin.asr.recognize(PcmAudio(byteArrayOf(1, 0))).text.startsWith("test.segmented"))
    }
    @Test fun managerOrdersEnginesAndOpensTheProvidersOwnModelPage() = runBlocking<Unit> {
        val broker = Broker(); val plugin = plugin(broker); val page = plugin.page("engines", PluginContext("", ""))!!.management!!
        val ids = page.items.drop(1).map { it.id }.reversed()
        plugin.execute("reorder", PluginContext("", "", inputs = mapOf("order" to PluginJson.encodeToString(ids))))
        assertEquals(ids, plugin(broker).page("engines", PluginContext("", ""))!!.management!!.items.drop(1).map { it.id })
        assertEquals(PluginOutcome.OpenPluginPage("test.online", "packs"), plugin.execute("settings/test.online/packs", PluginContext("", "")))
        assertEquals(PluginOutcome.SetPluginEnabled("test.online", false), plugin.execute("toggle/test.online", PluginContext("", "")))
        assertEquals(PluginOutcome.SelectRecognitionService("service.speech-recognition"), plugin.execute("activate", PluginContext("", "")))
        assertTrue(File(root, "config/recognition-service.json").isFile); assertFalse(File(root, "models").exists())
    }
    @Test fun batchOnlyProviderGetsFinalOnlyAdapterAndSilenceIsNotUploaded() = runBlocking<Unit> {
        val broker = Broker(); broker.catalog = listOf(AsrProvider("test.batch", "完整录音", AsrInfo(streaming = StreamingKind.NONE)))
        val plugin = plugin(broker); plugin.execute("select/test.batch", PluginContext("", ""))
        assertEquals(StreamingKind.SEGMENTED, plugin.asr.info.streaming)
        assertEquals(StreamingKind.NONE, broker.catalog.single().info.streaming)
        val silence = ByteArray(640 * 35)
        val speech = ByteArray(640 * 15) { if (it % 2 == 1) 10 else 0 }
        val events = plugin.asr.recognizeStream(flowOf(PcmAudio(silence + speech + silence + speech))).toList()
        assertEquals(RecognitionEvent.Started, events.first())
        val sentences = events.drop(1).map { it as RecognitionEvent.Transcript }
        assertEquals(listOf(1L, 2L), sentences.map { it.sequence })
        assertTrue(sentences.all { it.partial.isEmpty() && it.finalized!!.startsWith("test.batch") })
        assertEquals(listOf(RecognitionEvent.Started), plugin.asr.recognizeStream(flowOf(PcmAudio(silence))).toList())
        broker.catalog = emptyList(); assertEquals(EngineAvailability.NEEDS_RESOURCES, plugin.asr.info.availability)
        assertEquals(listOf("test.batch", "test.batch"), broker.calls)
    }

    @Test fun systemChoiceUsesTheExistingFinalOnlyCaptionPathWithoutRoutingAudioToAPlugin() = runBlocking {
        val external = Broker()
        val sizes = mutableListOf<Int>()
        val system = object : AsrEngine {
            override val info = AsrInfo(streaming = StreamingKind.NONE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult {
                sizes += audio.bytes.size
                return RecognitionResult("第${sizes.size}句")
            }
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = error("必须先断句")
        }
        val plugin = plugin(SystemRecognitionBroker(external, system))
        try {
            val picker = plugin.page("engines", PluginContext("", ""))!!.management!!
            assertEquals("系统识别引擎", picker.items.single { it.id == SYSTEM_RECOGNITION_ID }.title)
            plugin.execute("select/$SYSTEM_RECOGNITION_ID", PluginContext("", ""))
            assertEquals(StreamingKind.SEGMENTED, plugin.asr.info.streaming)
            val speech = ByteArray(640 * 15) { if (it % 2 == 1) 10 else 0 }
            val silence = ByteArray(640 * 35)
            val result = plugin.asr.recognizeStream(flowOf(PcmAudio(speech + silence + speech))).toList()
            assertEquals(listOf("第1句", "第2句"), result.filterIsInstance<RecognitionEvent.Transcript>().map { it.finalized })
            assertTrue(external.calls.isEmpty())
        } finally { plugin.close() }
    }
    @Test fun selectingDisabledProviderRequestsHostEnableWithoutInliningEngine() = runBlocking<Unit> {
        val broker = Broker(); broker.catalog = broker.catalog.map { it.copy(enabled = false) }; val plugin = plugin(broker)
        assertEquals(PluginOutcome.SetPluginEnabled("test.online", true), plugin.execute("select/test.online", PluginContext("", "")))
        assertEquals(EngineAvailability.NEEDS_RESOURCES, plugin.asr.info.availability)
        broker.catalog = broker.catalog.map { if (it.pluginId == "test.online") it.copy(enabled = true) else it }
        assertEquals(StreamingKind.INCREMENTAL, plugin.asr.info.streaming)
    }
    @Test fun serviceOwnsListeningAndVoiceGestureAndDoesNotDuplicateAnotherSelectedService() = runBlocking {
        val plugin = plugin(Broker()); val current = PluginContext("", "", recognitionServiceId = plugin.descriptor.id)
        plugin.execute("select/test.online", current)
        assertEquals("开始聆听", plugin.contributions(current).actions.first().label)
        assertEquals("关闭聆听", plugin.contributions(current.copy(listeningPanelVisible = true)).actions.first().label)
        assertEquals("input", plugin.contributions(current).gestures.single().id)
        val other = plugin.contributions(current.copy(recognitionServiceId = "other.service"))
        assertTrue(other.actions.isEmpty()); assertTrue(other.gestures.isEmpty()); assertEquals(1, other.settingsEntries.size)
        val caption = (plugin.execute("listen", current) as PluginOutcome.StartRecognition).session
        assertEquals(RecognitionPurpose.CAPTIONS, caption.purpose); assertFalse(caption.readFinalized)
        assertEquals(PluginOutcome.EndRecognition(), plugin.execute("listen", current.copy(listeningPanelVisible = true)))
        assertEquals(PluginOutcome.EndRecognition(), plugin.execute("close", current))
        val floating = current.copy(listening = true, listeningPanelVisible = false)
        assertEquals("关闭聆听", plugin.contributions(floating).actions.first().label)
        assertEquals(PluginOutcome.EndRecognition(), plugin.execute("listen", floating))
    }
    @Test fun continuousInputDisablesAutomaticReadThenOnlyFinalizedTextCanBeReadAndLongPressStops() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker); val context = PluginContext("", "")
        plugin.execute("select/test.online", context)
        val manual = (plugin.execute("input", context) as PluginOutcome.StartRecognition).session
        assertEquals(RecognitionPurpose.VOICE_INPUT, manual.purpose); assertFalse(manual.continuous)
        plugin.execute("continuous-input", context)
        val continuous = (plugin(broker).execute("input", context) as PluginOutcome.StartRecognition).session
        assertTrue(continuous.continuous); assertTrue(continuous.disableAutomaticRead); assertTrue(continuous.readFinalized)
        assertEquals(PluginOutcome.EndRecognition(), plugin.execute("input", context.copy(voiceInputActive = true, continuousRecognition = true)))
        assertEquals(PluginOutcome.EndRecognition(), plugin.execute("continuous", context.copy(continuousRecognition = true)))
        plugin.execute("voice-input", context)
        assertTrue(plugin(broker).contributions(context).gestures.isEmpty())
        assertEquals(PluginOutcome.RefreshUi, plugin.execute("input", context))
    }
    @Test fun disablingRecognitionPersistsAndOnlyReadyExplicitEngineEnablesActions() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker); val context = PluginContext("", "")
        assertTrue(plugin.execute("listen", context) is PluginOutcome.Message)
        assertTrue(plugin.contributions(context).gestures.isEmpty())
        plugin.execute("select/test.online", context)
        assertEquals(CaptionOutput.PANEL, (plugin(broker).execute("listen", context) as PluginOutcome.StartRecognition).session.output)
        assertEquals(PluginOutcome.RecognitionRoutingChanged, plugin.execute("disable-recognition", context))
        val reopened = plugin(broker)
        assertTrue(reopened.contributions(context).actions.isEmpty())
        assertEquals(EngineAvailability.NEEDS_RESOURCES, reopened.asr.info.availability)
        broker.catalog = listOf(AsrProvider("test.batch", "录音", AsrInfo(streaming = StreamingKind.NONE)))
        plugin.execute("select/test.batch", context)
        assertTrue(plugin.execute("listen", context) is PluginOutcome.StartRecognition)
        assertTrue(plugin.execute("continuous", context) is PluginOutcome.StartRecognition)
        assertTrue(plugin.execute("input", context) is PluginOutcome.StartRecognition)
        broker.catalog = emptyList()
        assertTrue(plugin.execute("input", context) is PluginOutcome.Message)
    }

    @Test fun migratingDisabledServicePreservesItsProviderAndStaysOffUntilExplicitlyEnabled() {
        val file = File(root, "config/recognition-service.json")
        file.parentFile!!.mkdirs()
        file.writeText("""{"provider":"test.online","continuousInput":true}""")
        val storage = PluginStorage(root)
        val migrated = RecognitionServiceSettings(storage) { false }
        assertEquals("test.online", migrated.provider)
        assertTrue(migrated.continuousInput)
        assertNull(migrated.current(Broker().catalog))
        migrated.routingDisabled = false
        migrated.save()
        assertEquals("test.online", RecognitionServiceSettings(storage) { false }.current(Broker().catalog)?.pluginId)
    }
    @Test fun longSessionPreservesRepeatedSentencesAndHypothesesNeverRewritePreviousFinals() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker)
        plugin.execute("select/test.online", PluginContext("", ""))
        broker.events = buildList {
            add(RecognitionEvent.Started)
            repeat(10_000) {
                add(RecognitionEvent.Partial("可能${it}")); add(RecognitionEvent.Partial("修改${it}"))
                add(RecognitionEvent.Final(RecognitionResult("同一句")))
            }
        }
        var finals = 0L; var previous = ""; var largest = 0
        plugin.asr.recognizeStream(emptyFlow(), RecognitionConfig(maxDisplayCharacters = 256)).collect { event ->
            if (event is RecognitionEvent.Transcript) {
                largest = maxOf(largest, event.committed.length + event.partial.length)
                if (event.finalized == null) assertTrue(previous.endsWith(event.committed))
                else { finals++; assertEquals("同一句", event.finalized); previous = event.committed }
                assertEquals(finals, event.sequence)
            }
        }
        assertEquals(10_000L, finals); assertTrue(largest <= 256)
        assertTrue(previous.endsWith("同一句\n同一句")); assertFalse(previous.contains("修改"))
    }
    @Test fun cancellationNeverTurnsUnconfirmedHypothesisIntoFinal() = runBlocking {
        val broker = Broker(); broker.hold = true; val plugin = plugin(broker)
        plugin.execute("select/test.online", PluginContext("", ""))
        val seen = mutableListOf<RecognitionEvent>()
        plugin.asr.recognizeStream(flowOf(PcmAudio(byteArrayOf(1, 0))))
            .onEach { seen += it }.first { it is RecognitionEvent.Transcript }
        assertTrue(broker.cancelled)
        assertTrue(seen.filterIsInstance<RecognitionEvent.Transcript>().none { it.finalized != null })
    }
    @Test fun idleRetentionAcceptsEndpointsPersistsAndRejectsOutOfRange() = runBlocking {
        val broker = Broker(); val plugin = plugin(broker)
        for (seconds in listOf(5, 120)) {
            plugin.execute("idle-seconds", PluginContext("", "", inputs = mapOf("value" to seconds.toString())))
            val groups = plugin(broker).page("manager", PluginContext("", ""))!!.blocks.filterIsInstance<PageBlock.Group>()
            val slider = groups.single { it.title == "加载方式" }.children.filterIsInstance<PageBlock.Slider>().single().control
            assertEquals(seconds.toFloat(), slider.value)
        }
        for (value in listOf("4", "121", "NaN")) {
            assertTrue(runCatching { plugin.execute("idle-seconds", PluginContext("", "", inputs = mapOf("value" to value))) }.isFailure)
        }
        plugin.close()
    }
}
