package com.kigtts.next

import android.app.Application
import com.kigtts.next.audio.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import com.kigtts.plugins.asr.PlaceholderAsrPlugin
import com.kigtts.plugins.tts.PlaceholderTtsPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/** Demo engines and synthetic PCM exist only on the test classpath. */
fun testAppModel(application: Application) = AppViewModel(application,
    listOf(PlaceholderTtsPlugin(), PlaceholderAsrPlugin()), object : AudioPlatform {
        override fun hasMicrophonePermission() = true
        override fun capture(format: AudioFormat) = flow {
            repeat(4) { delay(650); emit(PcmAudio(ByteArray(3200), format.sampleRate, format.channels)) }
        }
        override fun player() = object : PcmPlayer {
            override suspend fun write(audio: PcmAudio) {}
            override suspend fun finish() {}
            override fun close() {}
        }
    })
