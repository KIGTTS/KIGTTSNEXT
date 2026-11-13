package com.kigtts.next.ui.cards.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kigtts.next.media.color.CubeLut
import com.kigtts.plugin.api.PolaroidFormat
import java.io.File

/** The touch shutter and hardware keys use the same action, including an in-flight permission request. */
@Composable internal fun rememberCameraCaptureAction(session: PolaroidCameraSession, mode: CameraMode, ready: Boolean,
    format: PolaroidFormat, filter: PhotoFilter, options: CameraOptions, lut: CubeLut?,
    photo: (PolaroidCameraCapture) -> Unit, video: ((File, Boolean) -> Unit)?, message: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var permissionPending by remember { mutableStateOf(false) }
    val videoSaved = rememberUpdatedState(video)
    val currentMode = rememberUpdatedState(mode)
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (currentMode.value == CameraMode.VIDEO && session.ready && !session.video.active) {
            if (!granted) message("未开启麦克风，将录制无声视频")
            videoSaved.value?.let { session.startVideo(granted, false, it) }
        }
    }
    return {
        when {
            session.video.active -> session.video.stop()
            permissionPending || session.video.finishing -> Unit
            session.countingDown -> session.cancelCountdown()
            !ready -> Unit
            mode.recordsVideo && video != null && !session.busy -> {
                val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (mode == CameraMode.VIDEO && !audio) {
                    permissionPending = true; microphone.launch(Manifest.permission.RECORD_AUDIO)
                } else session.startVideo(audio, mode == CameraMode.TIME_LAPSE, video)
            }
            mode.takesPhotos -> {
                val output = CameraPhotoOutput(options.watermark, options.encoding)
                session.capture(format, filter, { photo(if (video != null) it.withOutput(output) else it) }, options.timerSeconds,
                    options.filterStrength, lut, options.effects, allowNextExposure = video != null && !session.motionAvailable)
            }
            else -> Unit
        }
    }
}
