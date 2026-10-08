package com.kigtts.next.audio

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.Flow

/** Hardware transport only. No recognition/synthesis models, SDKs or engine settings. */
interface AudioPlatform {
    fun hasMicrophonePermission(): Boolean
    fun capture(format: AudioFormat): Flow<PcmAudio>
    fun player(): PcmPlayer
}
interface PcmPlayer {
    suspend fun write(audio: PcmAudio)
    suspend fun finish()
    fun close()
}
