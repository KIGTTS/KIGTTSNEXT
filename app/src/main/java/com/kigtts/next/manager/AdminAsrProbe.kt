package com.kigtts.next.manager

import com.kigtts.plugin.api.AsrEngine
import com.kigtts.plugin.api.PcmAudio
import com.kigtts.plugin.api.RecognitionEvent
import com.kigtts.plugin.api.StreamingKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Exercise the installed worker with a supplied fixture; never opens the microphone. */
internal suspend fun probeInstalledAsr(engine: AsrEngine, fixture: File): JSONObject {
    val format = engine.info.inputFormat
    require(engine.info.streaming == StreamingKind.INCREMENTAL) { "当前引擎不支持实时流式识别" }
    require(format.sampleRate == 16_000 && format.channels == 1) { "自检仅支持 16 kHz 单声道 PCM" }
    require(fixture.length() in 1600..960_000 && fixture.length() % 2 == 0L) { "请提供最多 30 秒的 16 位裸 PCM" }
    val bytes = fixture.readBytes()
    val eof = AtomicBoolean(false)
    var partials = 0
    var finals = 0
    fun read(partial: String, final: String?) {
        if (!eof.get() && partial.isNotBlank()) partials++
        if (!final.isNullOrBlank()) finals++
    }
    withTimeout(60_000) {
        engine.recognizeStream(flow {
            for (offset in bytes.indices step 3200) {
                emit(PcmAudio(bytes.copyOfRange(offset, minOf(offset + 3200, bytes.size)), 16_000, 1))
                delay(60)
            }
            eof.set(true)
        }).collect { event ->
            when (event) {
                RecognitionEvent.Started -> Unit
                is RecognitionEvent.Partial -> read(event.text, null)
                is RecognitionEvent.Final -> read("", event.result.text)
                is RecognitionEvent.Transcript -> read(event.partial, event.finalized)
            }
        }
    }
    check(partials > 0 && finals > 0) { "未得到流结束前的实时识别和最终结果：partials=$partials, finals=$finals" }
    return JSONObject().put("partialsBeforeEof", partials).put("finals", finals)
}
