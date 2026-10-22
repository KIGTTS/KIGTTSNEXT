package com.kigtts.next.ui.cards

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

/** Camera and picker results are imported by the caller before the temporary capture is discarded. */
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
    var captureName by rememberSaveable { mutableStateOf<String?>(null) }
    var fullCameraStart by rememberSaveable { mutableLongStateOf(0L) }
    var fullCameraPreviousId by rememberSaveable { mutableLongStateOf(0L) }
    val captures = remember(context) { File(context.cacheDir, "cards/captures") }
    fun captureFile(name: String) = File(captures, name).also {
        require(it.canonicalFile.parentFile == captures.canonicalFile)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = captureName; captureName = null
        if (name != null) {
            val file = captureFile(name)
            if (success && file.isFile && file.length() > 0) {
                // The external camera owns capture quality. Never re-encode/downsample its
                // JPEG; Polaroid framing is stored separately from this original file.
                try { selected.value(CardPhotoSelection(Uri.fromFile(file), fromCamera = true) { file.delete() }) }
                catch (_: Exception) { file.delete(); failure.value("无法处理拍摄的照片，请重试") }
            } else {
                file.delete()
                if (success) failure.value("照片未能保存，请重试") else cancelled.value()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
                val uri = supplied ?: receiveSystemPhoto(context, previous, started)
                if (uri != null) selected.value(CardPhotoSelection(uri, fromCamera = true)) else cancelled.value()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure.value("未能接收系统相机的新照片，请重试") }
            finally { processing = false }
        }
    }
    fun launchCaptureIntent() {
        check(captures.mkdirs() || captures.isDirectory)
        val name = UUID.randomUUID().toString() + ".jpg"
        captureName = name
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", captureFile(name))
        camera.launch(uri)
    }
    fun launchFullCamera() {
        fullCameraPreviousId = SystemCameraPhotos.newestId(context)
        fullCameraStart = System.currentTimeMillis()
        try { fullCamera.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        catch (error: Exception) { fullCameraStart = 0L; throw error }
    }
    val photosPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        try { if (allowed) launchFullCamera() else launchCaptureIntent() }
        catch (_: Exception) { failure.value("无法打开系统相机，请重试") }
    }
    fun launchCamera() {
        try {
            if (context.getSharedPreferences("polaroid_camera", 0).getBoolean("full-system-camera", true)) {
                // The ordinary camera activity owns its lens UI. Capture-intent mode is restricted by some OEMs.
                if (ContextCompat.checkSelfPermission(context, SystemCameraPhotos.permission) == PackageManager.PERMISSION_GRANTED) launchFullCamera()
                else photosPermission.launch(SystemCameraPhotos.permission)
                return
            }
            launchCaptureIntent()
        } catch (_: ActivityNotFoundException) {
            captureName?.let { captureFile(it).delete() }; captureName = null
            failure.value("没有可用的相机应用")
        } catch (_: Exception) {
            captureName?.let { captureFile(it).delete() }; captureName = null
            failure.value("无法打开相机，请重试")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) launchCamera() else failure.value("需要相机权限才能通过系统相机拍照")
    }
    return CardPhotoSources(camera = {
        if (processing || fullCameraStart != 0L || captureName != null) return@CardPhotoSources
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }, gallery = {
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: ActivityNotFoundException) { failure.value("无法打开图片选择器") }
    }, processing = processing)
}
