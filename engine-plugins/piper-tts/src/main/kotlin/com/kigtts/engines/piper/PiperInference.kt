// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.piper

import ai.onnxruntime.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Port of the old ONNX inference path. Model, tensors and native state never enter the host. */
internal suspend fun piperInference(environment: PluginEnvironment, pack: PiperPack, speaker: Int,
    request: TtsRequest): PcmAudio {
    val toIds = if (pack.phonemeType.contains("espeak")) {
        EspeakPhonemizer(environment.assets, ensureEspeakData(environment), pack.espeakVoice, pack.ids, pack.phoneMap)::toIds
    } else PiperPhonemizer(pack.dictionary, pack.ids, pack.phoneMap)::toIds
    val ids = toIds(request.text).map { it.toLong() }.toLongArray()
    require(ids.size >= 3) { "文本无法转换为此语音包的音素" }
    currentCoroutineContext().ensureActive()
    val runtime = OrtEnvironment.getEnvironment()
    OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
        runtime.createSession(pack.model.path, options).use { session ->
            val inputName = session.inputNames.firstOrNull { it == "input" } ?: session.inputNames.first { it.contains("input") && !it.contains("length") }
            val lengthName = session.inputNames.first { it.contains("length") || it.contains("len") }
            val inputs = linkedMapOf<String, OnnxTensor>()
            try {
                inputs[inputName] = OnnxTensor.createTensor(runtime, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
                inputs[lengthName] = OnnxTensor.createTensor(runtime, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
                session.inputNames.firstOrNull { it.contains("scale") }?.let {
                    inputs[it] = OnnxTensor.createTensor(runtime, FloatBuffer.wrap(floatArrayOf(.667f, 1f / request.rate, .8f)), longArrayOf(3))
                }
                session.inputNames.firstOrNull { it.contains("sid") }?.let {
                    inputs[it] = OnnxTensor.createTensor(runtime, LongBuffer.wrap(longArrayOf(speaker.toLong())), longArrayOf(1))
                }
                session.run(inputs).use { result ->
                    currentCoroutineContext().ensureActive()
                    val samples = unwrap(result[0].value)
                    require(samples.isNotEmpty()) { "Piper 未产生音频" }
                    val pcm = ByteArray(samples.size * 2)
                    samples.forEachIndexed { index, value ->
                        val sample = (value.coerceIn(-1f, 1f) * 32767).toInt()
                        pcm[index * 2] = sample.toByte(); pcm[index * 2 + 1] = (sample shr 8).toByte()
                    }
                    return PcmAudio(pcm, pack.sampleRate)
                }
            } finally { inputs.values.forEach { it.close() } }
        }
    }
}
private fun unwrap(value: Any?): FloatArray = when (value) {
    is FloatArray -> value
    is Array<*> -> value.firstOrNull()?.let(::unwrap) ?: FloatArray(0)
    else -> FloatArray(0)
}
