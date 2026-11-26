package com.kigtts.next.quickactions

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/** Publish the last committed subtitle through Bluetooth media metadata, without playing audio. */
internal class BluetoothSubtitleOutput(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var subtitle = ""
    private var published = ""
    private var observing = false
    private val send = Runnable { publish() }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = publish()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = publish()
    }

    fun setEnabled(enabled: Boolean) {
        if (observing == enabled) return
        observing = enabled
        if (enabled) {
            audio.registerAudioDeviceCallback(devices, main)
            publish()
        } else {
            main.removeCallbacks(send)
            audio.unregisterAudioDeviceCallback(devices)
            release()
        }
    }

    fun update(text: String) {
        val normalized = bluetoothSubtitleTitle(text)
        if (normalized == subtitle) return
        subtitle = normalized
        if (!observing) return
        main.removeCallbacks(send)
        if (subtitle.isEmpty()) release() else main.postDelayed(send, 120)
    }

    private fun publish() {
        if (!observing || subtitle.isEmpty() || audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in bluetoothMediaOutputs }) {
            release()
            return
        }
        if (published == subtitle) return
        val active = session ?: MediaSession(context, "KIGTTS.BluetoothSubtitles").also { session = it }
        active.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "kigtts-subtitle")
            .putString(MediaMetadata.METADATA_KEY_TITLE, subtitle)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, subtitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "KIGTTS")
            .build())
        if (!active.isActive) {
            // AVRCP displays can ignore inactive and stopped sessions. No audio focus or media keys are claimed.
            active.setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build())
            active.isActive = true
        }
        published = subtitle
    }

    private fun release() {
        session?.let { it.isActive = false; it.release() }
        session = null
        published = ""
    }
}

private val bluetoothMediaOutputs = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID)
private val titleWhitespace = Regex("[\\p{Z}\\s\\p{Cc}]+")

internal fun bluetoothSubtitleTitle(text: String): String {
    val clean = titleWhitespace.replace(text.take(4096), " ").trim()
    val limit = 64
    if (clean.codePointCount(0, clean.length) <= limit) return clean
    return clean.substring(0, clean.offsetByCodePoints(0, limit - 1)).trimEnd() + "…"
}
