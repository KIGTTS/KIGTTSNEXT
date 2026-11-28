package com.kigtts.next.ui.cards

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import com.kigtts.next.ui.media.VisualMediaPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kigtts.next.i18n.uiCopy
import kotlinx.coroutines.*

/** Camera and picker results are imported by the caller. */
internal data class CardPhotoSelection(val uri: Uri, val fromCamera: Boolean = false, val release: () -> Unit = {})
internal data class CardPhotoSources(val camera: () -> Unit, val gallery: () -> Unit, val processing: Boolean)

@Composable
internal fun rememberCardPhotoSources(onSelected: (CardPhotoSelection) -> Unit,
    onError: (String) -> Unit, onCameraCancelled: () -> Unit = {},
    receiveSystemPhoto: suspend (android.content.Context, Long, Long) -> Uri? = SystemCameraPhotos::receive): CardPhotoSources {
    val context = LocalContext.current
    val selected = rememberUpdatedState(onSelected)
    val failure = rememberUpdatedState(onError)
    val cancelled = rememberUpdatedState(onCameraCancelled)
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    var fullCameraStart by rememberSaveable { mutableLongStateOf(0L) }
    var fullCameraPreviousId by rememberSaveable { mutableLongStateOf(0L) }
    fun canReadPhotos() =
        ContextCompat.checkSelfPermission(context, SystemCameraPhotos.permission) == PackageManager.PERMISSION_GRANTED
    val picker = rememberLauncherForActivityResult(VisualMediaPicker()) { uri ->
        if (uri != null) selected.value(CardPhotoSelection(uri))
    }
    val fullCamera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (fullCameraStart == 0L) return@rememberLauncherForActivityResult
        val started = fullCameraStart; val previous = fullCameraPreviousId; fullCameraStart = 0L
        processing = true
        scope.launch {
            try {
                val supplied = result.data?.data?.takeIf { it.scheme == "content" &&
                    runCatching { context.contentResolver.getType(it)?.startsWith("image/") == true }.getOrDefault(false) }
                val uri = supplied ?: if (canReadPhotos()) receiveSystemPhoto(context, previous, started) else null
                if (uri != null) selected.value(CardPhotoSelection(uri, fromCamera = true)) else cancelled.value()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure.value(context.uiCopy("camera.import_photo_failed")) }
            finally { processing = false }
        }
    }
    fun launchFullCamera() {
        try {
            fullCameraPreviousId = if (canReadPhotos()) SystemCameraPhotos.newestId(context) else 0L
            fullCameraStart = System.currentTimeMillis()
            fullCamera.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (_: Exception) {
            fullCameraStart = 0L
            failure.value(context.uiCopy("camera.system_missing"))
        }
    }
    val photosPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchFullCamera() }
    return CardPhotoSources(camera = {
        if (processing || fullCameraStart != 0L) return@CardPhotoSources
        if (canReadPhotos()) launchFullCamera() else photosPermission.launch(SystemCameraPhotos.permission)
    }, gallery = {
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: ActivityNotFoundException) { failure.value("无法打开图片选择器") }
    }, processing = processing)
}
