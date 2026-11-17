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

/** Exercise the installed engine through the supplied PCM, without starting the app's microphone. */
internal suspend fun probeInstalledAsr(engine: AsrEngine, fixture: File, segmented: Boolean = false): JSONObject {
    val format = engine.info.inputFormat
    val expected = if (segmented) StreamingKind.SEGMENTED else StreamingKind.INCREMENTAL
    require(engine.info.streaming == expected) { "当前引擎不支持所选的识别自检模式" }
    require(format.sampleRate == 16_000 && format.channels == 1) { "自检仅支持 16 kHz 单声道 PCM" }
    require(fixture.length() in 1600..960_000 && fixture.length() % 2 == 0L) { "请提供最多 30 秒的 16 位裸 PCM" }
    val bytes = fixture.readBytes()
    val eof = AtomicBoolean(false)
    var partials = 0
    var finals = 0
    var anyPartial = false
    fun read(partial: String, final: String?) {
        if (partial.isNotBlank()) {
            anyPartial = true
            if (!eof.get()) partials++
        }
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
    check(finals > 0 && if (segmented) !anyPartial else partials > 0) {
        "未得到所选模式的识别结果：partials=$partials, finals=$finals"
    }
    return JSONObject().put("partialsBeforeEof", partials).put("finals", finals)
}
