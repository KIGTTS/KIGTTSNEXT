package com.kigtts.next.ui.cards

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

/** Camera and picker results are imported by the caller before the temporary capture is discarded. */
internal data class CardPhotoSelection(val uri: Uri, val release: () -> Unit = {})
internal data class CardPhotoSources(val camera: () -> Unit, val gallery: () -> Unit, val processing: Boolean)

@Composable
internal fun rememberCardPhotoSources(onSelected: (CardPhotoSelection) -> Unit,
    onError: (String) -> Unit): CardPhotoSources {
    val context = LocalContext.current
    val selected = rememberUpdatedState(onSelected)
    val failure = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    var captureName by rememberSaveable { mutableStateOf<String?>(null) }
    val captures = remember(context) { File(context.cacheDir, "cards/captures") }
    fun captureFile(name: String) = File(captures, name).also {
        require(it.canonicalFile.parentFile == captures.canonicalFile)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = captureName; captureName = null
        if (name != null) {
            val file = captureFile(name)
            if (success && file.isFile && file.length() > 0) {
                val cropped = captureFile(name.removeSuffix(".jpg") + "-polaroid.jpg")
                processing = true
                scope.launch {
                    var delivered = false
                    try {
                        withContext(Dispatchers.IO) { cropCameraPhoto(file, cropped) }
                        selected.value(CardPhotoSelection(Uri.fromFile(cropped)) { cropped.delete() }); delivered = true
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failure.value("无法处理拍摄的照片，请重试") }
                    finally { file.delete(); if (!delivered) cropped.delete(); processing = false }
                }
            } else {
                file.delete()
                if (success) failure.value("照片未能保存，请重试")
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selected.value(CardPhotoSelection(uri))
    }
    return CardPhotoSources(camera = {
        try {
            check(captures.mkdirs() || captures.isDirectory)
            val name = UUID.randomUUID().toString() + ".jpg"
            captureName = name
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", captureFile(name))
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            captureName?.let { captureFile(it).delete() }; captureName = null
            failure.value("没有可用的相机应用")
        } catch (_: Exception) {
            captureName?.let { captureFile(it).delete() }; captureName = null
            failure.value("无法打开相机，请重试")
        }
    }, gallery = {
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: ActivityNotFoundException) { failure.value("无法打开图片选择器") }
    }, processing = processing)
}
