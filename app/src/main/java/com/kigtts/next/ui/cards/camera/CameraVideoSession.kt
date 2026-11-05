package com.kigtts.next.ui.cards.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.video.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/** Finalization owns its file independently from preview recomposition and route changes. */
internal class CameraVideoSession(private val context: Context, private val message: (String) -> Unit) {
    private var recording: Recording? = null
    var active by mutableStateOf(false); private set
    var finishing by mutableStateOf(false); private set
    var durationMs by mutableLongStateOf(0); private set
    fun start(recorder: Recorder?, audio: Boolean, timeLapse: Boolean, saved: (File, Boolean) -> Unit) {
        if (active || finishing) return
        if (recorder == null) { message("当前镜头暂不支持录像"); return }
        if (context.cacheDir.usableSpace < 128L * 1024 * 1024) { message("存储空间不足"); return }
        val directory = File(context.filesDir, "pending-camera-video").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        try {
            var pending = recorder.prepareRecording(context, options)
            if (audio && !timeLapse && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                pending = pending.withAudioEnabled()
            active = true; durationMs = 0
            recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                if (event is VideoRecordEvent.Finalize) {
                    recording = null; active = false; finishing = false
                    if (event.error in setOf(VideoRecordEvent.Finalize.ERROR_NONE, VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED, VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) && file.length() > 0)
                        saved(file, timeLapse)
                    else { file.delete(); message("录像未能完成，请重试") }
                }
            }
        } catch (_: Exception) { active = false; finishing = false; file.delete(); message("录像暂时无法开始") }
    }
    fun stop() { if (recording != null && !finishing) { finishing = true; recording?.stop() } }
}
