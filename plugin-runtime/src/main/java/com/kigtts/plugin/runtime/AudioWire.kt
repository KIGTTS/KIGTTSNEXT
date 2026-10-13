package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/** Pipes provide bounded backpressure without Binder's transaction-size limit. PCM stays binary. */
internal object AudioWire {
    const val TRANSCRIPT = 8
    const val START = 1; const val AUDIO = 2; const val FINISH = 3
    const val PARTIAL = 4; const val FINAL = 5; const val ERROR = 6
    const val END_INPUT = 7
    const val MAX_FRAME = 128 * 1024
    const val MAX_TEXT = 256 * 1024
    fun pcm(out: DataOutputStream, audio: PcmAudio) {
        val stride = 2 * audio.channels
        val chunk = MAX_FRAME / stride * stride
        for (offset in audio.bytes.indices step chunk) {
            val size = minOf(chunk, audio.bytes.size - offset)
            out.writeInt(AUDIO); out.writeInt(audio.sampleRate); out.writeInt(audio.channels)
            out.writeInt(size); out.write(audio.bytes, offset, size); out.flush()
        }
    }
    fun readPcm(input: DataInputStream): PcmAudio {
        val rate = input.readInt(); val channels = input.readInt(); val size = input.readInt()
        require(size in 0..MAX_FRAME)
        return PcmAudio(ByteArray(size).also(input::readFully), rate, channels)
    }
    fun text(out: DataOutputStream, tag: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8); require(bytes.size <= MAX_TEXT)
        out.writeInt(tag); out.writeInt(bytes.size); out.write(bytes); out.flush()
    }
    fun readText(input: DataInputStream): String {
        val length = input.readInt(); require(length in 0..MAX_TEXT)
        return ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
    }
    fun tag(input: DataInputStream): Int? = try { input.readInt() } catch (_: EOFException) { null }
    fun error(out: DataOutputStream, error: Exception) = text(out, ERROR,
        PluginJson.encodeToString(WireError((error as? EngineException)?.code ?: EngineError.INFERENCE,
            error.message?.take(1000) ?: "插件任务失败")))
    fun failure(input: DataInputStream): Nothing {
        val error = PluginJson.decodeFromString<WireError>(readText(input))
        throw EngineException(error.code, error.message)
    }
}

@kotlinx.serialization.Serializable
internal data class WireError(val code: EngineError, val message: String)
