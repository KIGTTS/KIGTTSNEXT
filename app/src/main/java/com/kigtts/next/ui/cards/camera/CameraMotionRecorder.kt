package com.kigtts.next.ui.cards.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File

/** Each buffer is tied to one binding. Audio is enabled only with an existing microphone grant. */
internal class CameraMotionRecorder(private val context: Context, private val recorder: Recorder) : CameraMotionSource {
    override fun start(file: File, events: (CameraMotionEvent) -> Unit): CameraMotionRecording {
        val output = FileOutputOptions.Builder(file).setDurationLimitMillis(CameraMotionBuffer.SEGMENT_MS)
            .setFileSizeLimit(24L * 1024 * 1024).build()
        fun begin(audio: Boolean): androidx.camera.video.Recording {
            var pending = recorder.prepareRecording(context, output)
            if (audio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pending = pending.withAudioEnabled()
            }
            return pending.start(ContextCompat.getMainExecutor(context)) { event ->
                val duration = event.recordingStats.recordedDurationNanos / 1_000_000
                when (event) {
                    is VideoRecordEvent.Start -> events(CameraMotionEvent.Started(duration))
                    is VideoRecordEvent.Status -> events(CameraMotionEvent.Progress(duration))
                    is VideoRecordEvent.Finalize -> {
                        val usable = event.error in setOf(VideoRecordEvent.Finalize.ERROR_NONE,
                            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED)
                        if (!usable) Log.w(CAMERA_LOG_TAG, "Live Photo recorder stopped: ${event.error} at ${duration} ms", event.cause)
                        events(CameraMotionEvent.Finished(duration, usable,
                            event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ||
                                event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE))
                    }
                }
            }
        }
        val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val recording = try { begin(audio) } catch (_: SecurityException) { begin(false) }
        return CameraMotionRecording { recording.stop() }
    }
}
