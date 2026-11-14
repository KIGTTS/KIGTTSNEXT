package com.kigtts.next

import com.kigtts.next.audio.*
import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionSessionTest {
    private class Hardware : AudioPlatform {
        var permission = true; var captured = 0; var released = 0
        override fun hasMicrophonePermission() = permission
        override fun capture(format: AudioFormat) = flow {
            captured++
            try { emit(PcmAudio(ByteArray(640))); awaitCancellation() } finally { released++ }
        }
        override fun player() = object : PcmPlayer { override suspend fun write(audio: PcmAudio) {}; override suspend fun finish() {}; override fun close() {} }
    }
    private fun plugin(engine: AsrEngine, synthesis: TtsEngine? = null) = object : KigPlugin {
        override val descriptor = PluginDescriptor("test.service", "测试", "测试")
        override val asr = engine; override val tts = synthesis
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun emptyRecognitionDoesNotCreateUserFacingNoiseButRealErrorsStillReport() = runTest {
        var state = AppState(); val messages = mutableListOf<String>()
        var fail = false
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow<RecognitionEvent> {
                emit(RecognitionEvent.Started); audio.collect { }; if (fail) error("麦克风处理失败")
            }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine))), Hardware(), { state }, { state = it(state) }, messages::add, {}, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)); runCurrent(); controller.end(true); advanceUntilIdle()
        assertTrue(messages.isEmpty()); assertFalse(state.voiceInputActive)
        fail = true
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)); runCurrent(); controller.end(true); advanceUntilIdle()
        assertEquals(listOf("麦克风处理失败"), messages); controller.stopAll()
    }
    @Test fun quickSubmitAllowsColdInitializationBeforeTheFinalFlushBudget() = runTest {
        var state = AppState(autoPlayback = false)
        val submitted = mutableListOf<String>(); val messages = mutableListOf<String>()
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
                delay(5_000); emit(RecognitionEvent.Started)
                audio.collect { }
                emit(RecognitionEvent.Transcript("冷启动后的完整句", "", 1, "冷启动后的完整句"))
            }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine))), Hardware(), { state }, { state = it(state) },
            messages::add, submitted::add, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)); runCurrent()
        controller.end(true); advanceTimeBy(3_500); runCurrent()
        assertTrue(state.voiceInputActive); assertTrue(messages.isEmpty()); assertTrue(submitted.isEmpty())
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("冷启动后的完整句"), submitted); assertTrue(messages.isEmpty()); assertFalse(state.voiceInputActive)
        controller.stopAll()
    }
    @Test fun submitSendsEofFlushesFinalAndCancelNeverSubmits() = runTest {
        val hardware = Hardware(); var state = AppState(); val submitted = mutableListOf<String>()
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
                audio.collect { emit(RecognitionEvent.Transcript("", "临时", 0)) }
                emit(RecognitionEvent.Transcript("完整句", "", 1, "完整句"))
            }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine))), hardware, { state }, { state = it(state) }, {}, submitted::add, { "test.service" })
        val session = RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)
        controller.start(session); runCurrent(); assertEquals("临时", state.voiceInputText)
        controller.end(true); runCurrent()
        assertEquals(listOf("完整句"), submitted); assertEquals(1, hardware.released); assertFalse(state.voiceInputActive)
        controller.start(session); runCurrent(); controller.end(false); runCurrent()
        assertEquals(listOf("完整句"), submitted); assertEquals(2, hardware.released)
        controller.stopAll()
    }
    @Test fun segmentedEngineCanReturnTheLastSentenceAfterTheStreamingDeadline() = runTest {
        var state = AppState(autoPlayback = false)
        val submitted = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE, streaming = StreamingKind.SEGMENTED)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("the service adapts batch requests")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
                emit(RecognitionEvent.Started)
                audio.collect {}
                delay(5_000)
                emit(RecognitionEvent.Transcript("最后一句", "", 1, "最后一句"))
            }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine))), Hardware(), { state }, { state = it(state) },
            messages::add, submitted::add, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT))
        runCurrent()
        controller.end(true)
        advanceTimeBy(3_500); runCurrent()
        assertTrue(state.voiceInputActive)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("最后一句"), submitted)
        assertTrue(messages.isEmpty())
        controller.stopAll()
    }
    @Test fun continuousOnlyQueuesFinalizedSequencesAfterUserReenablesReading() = runTest {
        val events = Channel<RecognitionEvent>(Channel.UNLIMITED)
        val spoken = mutableListOf<String>(); val submitted = mutableListOf<String>()
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = events.receiveAsFlow()
        }
        val synthesis = object : TtsEngine {
            override fun synthesize(request: TtsRequest) = flow { spoken += request.text; emit(SynthesisEvent.Finished()) }
        }
        var state = AppState(autoPlayback = true)
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine, synthesis))), Hardware(), { state }, { state = it(state) }, {}, submitted::add, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT, continuous = true, disableAutomaticRead = true, readFinalized = true))
        runCurrent(); assertFalse(state.autoPlayback)
        events.send(RecognitionEvent.Transcript("第一句", "", 1, "第一句")); runCurrent()
        state = state.copy(autoPlayback = true)
        events.send(RecognitionEvent.Transcript("第一句", "未固化", 1)); runCurrent()
        events.send(RecognitionEvent.Transcript("第一句\n第二句", "", 2, "第二句")); runCurrent()
        events.send(RecognitionEvent.Transcript("第一句\n第二句", "", 2, "第二句")); runCurrent()
        events.send(RecognitionEvent.Transcript("第一句\n第二句\n第三句", "", 3, "第三句")); runCurrent()
        assertEquals(listOf("第一句", "第二句", "第三句"), submitted)
        assertEquals(listOf("第二句", "第三句"), spoken)
        assertEquals("第一句\n第二句\n第三句", state.voiceInputText)
        controller.end(); runCurrent(); assertFalse(state.continuousRecognition)
        controller.stopAll(); events.close()
    }
    @Test fun switchingSessionsJoinsOldMicrophoneAndPermissionCancellationDoesNotRestart() = runTest {
        val hardware = Hardware(); var state = AppState()
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow { audio.collect { emit(RecognitionEvent.Partial("字幕")) } }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine))), hardware, { state }, { state = it(state) }, {}, {}, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.CAPTIONS)); runCurrent()
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)); runCurrent()
        assertEquals(2, hardware.captured); assertEquals(1, hardware.released)
        controller.stopAll(); hardware.permission = false
        controller.start(RecognitionSession("test.service", RecognitionPurpose.CAPTIONS)); controller.closeListening()
        hardware.permission = true; controller.permissionResult(true); runCurrent()
        assertEquals(2, hardware.captured); assertFalse(state.listening)
    }
    @Test fun promotingContinuousKeepsMicrophoneAndCommitsOnlyStableText() = runTest {
        val events = Channel<RecognitionEvent>(Channel.UNLIMITED)
        val hardware = Hardware(); var state = AppState(); val submitted = mutableListOf<String>()
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = channelFlow {
                val capture = launch { audio.collect {} }
                try { events.receiveAsFlow().collect { send(it) } } finally { capture.cancelAndJoin() }
            }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine))), hardware, { state }, { state = it(state) },
            {}, submitted::add, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)); runCurrent()
        events.send(RecognitionEvent.Transcript("已经说完", "尚未确认", 1, "已经说完")); runCurrent()
        controller.continueInput(); runCurrent()
        assertEquals(1, hardware.captured); assertEquals(0, hardware.released)
        assertTrue(state.continuousRecognition); assertFalse(state.autoPlayback)
        assertEquals(listOf("已经说完"), submitted)
        events.send(RecognitionEvent.Transcript("已经说完\n第二句", "", 2, "第二句")); runCurrent()
        assertEquals(listOf("已经说完", "第二句"), submitted)
        controller.stopAll(); assertEquals(1, hardware.released); events.close()
    }
    @Test fun explicitSendAndReadWorksWithAutomaticReadingOffWithoutChangingPreference() = runTest {
        val spoken = mutableListOf<String>(); val submitted = mutableListOf<String>(); var state = AppState(autoPlayback = false)
        val engine = object : AsrEngine {
            override val info = AsrInfo(role = AsrRole.SERVICE)
            override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig) = error("not batch")
            override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
                emit(RecognitionEvent.Started); audio.collect {}
                emit(RecognitionEvent.Transcript("一次朗读", "", 1, "一次朗读"))
            }
        }
        val synthesis = object : TtsEngine {
            override fun synthesize(request: TtsRequest) = flow { spoken += request.text; emit(SynthesisEvent.Finished()) }
        }
        val controller = SpeechCoordinator(this, PluginRegistry(listOf(plugin(engine, synthesis))), Hardware(), { state },
            { state = it(state) }, {}, submitted::add, { "test.service" })
        controller.start(RecognitionSession("test.service", RecognitionPurpose.VOICE_INPUT)); runCurrent()
        controller.end(true, read = true); runCurrent()
        assertEquals(listOf("一次朗读"), submitted); assertEquals(listOf("一次朗读"), spoken)
        assertFalse(state.autoPlayback); controller.stopAll()
    }
}
