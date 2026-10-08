package com.kigtts.next.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.AudioFormat as NativeAudioFormat
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AndroidAudioPlatform(private val context: Context) : AudioPlatform {
    override fun hasMicrophonePermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    @Suppress("MissingPermission")
    override fun capture(format: AudioFormat) = flow {
        check(hasMicrophonePermission()) { "需要麦克风权限" }
        val channel = if (format.channels == 1) NativeAudioFormat.CHANNEL_IN_MONO else NativeAudioFormat.CHANNEL_IN_STEREO
        val minimum = AudioRecord.getMinBufferSize(format.sampleRate, channel, NativeAudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "设备不支持此录音格式" }
        val chunkSize = format.sampleRate / 50 * format.channels * 2
        val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(NativeAudioFormat.Builder().setSampleRate(format.sampleRate).setEncoding(NativeAudioFormat.ENCODING_PCM_16BIT).setChannelMask(channel).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, chunkSize * 4)).build()
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
            recorder.startRecording()
            val buffer = ByteArray(chunkSize)
            while (currentCoroutineContext().isActive) {
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "麦克风读取失败（$count）" }
                if (count == 0) delay(10) else emit(PcmAudio(buffer.copyOf(count), format.sampleRate, format.channels))
            }
        } finally { runCatching { recorder.stop() }; recorder.release() }
    }.flowOn(Dispatchers.IO)
    override fun player(): PcmPlayer = AndroidPcmPlayer()
}

private class AndroidPcmPlayer : PcmPlayer {
    private var track: AudioTrack? = null
    private var currentFormat: com.kigtts.plugin.api.AudioFormat? = null
    private var frames = 0L
    override suspend fun write(audio: PcmAudio) = withContext(Dispatchers.IO) {
        val format = com.kigtts.plugin.api.AudioFormat(audio.sampleRate, audio.channels)
        if (format != currentFormat) {
            finish(); close()
            val mask = if (audio.channels == 1) NativeAudioFormat.CHANNEL_OUT_MONO else NativeAudioFormat.CHANNEL_OUT_STEREO
            val minimum = AudioTrack.getMinBufferSize(audio.sampleRate, mask, NativeAudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(NativeAudioFormat.Builder().setEncoding(NativeAudioFormat.ENCODING_PCM_16BIT).setSampleRate(audio.sampleRate).setChannelMask(mask).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum * 2).build().also { it.play() }
            currentFormat = format; frames = 0
        }
        var offset = 0
        while (offset < audio.bytes.size) {
            currentCoroutineContext().ensureActive()
            val count = requireNotNull(track).write(audio.bytes, offset, audio.bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            check(count >= 0) { "音频播放失败（$count）" }
            offset += count
            if (count == 0) delay(5)
        }
        frames += audio.bytes.size / (2 * audio.channels)
    }
    override suspend fun finish() {
        val player = track ?: return
        withTimeout(30_000) { while ((player.playbackHeadPosition.toLong() and 0xffffffffL) < frames) delay(10) }
    }
    override fun close() { track?.let { runCatching { it.stop() }; it.release() }; track = null; currentFormat = null; frames = 0 }
}
