package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.media.saveGalleryImage
import com.kigtts.next.ui.components.feedbackClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** The original file powers preview and saving, including animated GIFs. */
@Composable internal fun ProfileImagePreview(file: File, description: String, dismiss: () -> Unit,
    save: suspend (android.content.Context, File) -> Unit = { context, image -> saveGalleryImage(context, image); Unit }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    val snack = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(visibility.currentState, visibility.isIdle) {
        if (visibility.isIdle && !visibility.currentState && !visibility.targetState) dismiss()
    }
    Dialog({ visibility.targetState = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AnimatedVisibility(visibility, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f)).testTag("profile-image-preview")) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(feedbackClick { visibility.targetState = false }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                            Icon(Icons.Default.Close, uiText("accessibility.close_image"))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(file) {
                        detectTransformGestures { _, delta, scale, _ ->
                            val next = (zoom * scale).coerceIn(1f, 5f)
                            zoom = next
                            pan = if (next <= 1.001f) Offset.Zero else (pan + delta).let {
                                Offset(it.x.coerceIn(-size.width * (next - 1) / 2, size.width * (next - 1) / 2),
                                    it.y.coerceIn(-size.height * (next - 1) / 2, size.height * (next - 1) / 2))
                            }
                        }
                    }) {
                        ProfileTileImage(file, Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                        }, description, fit = true)
                    }
                    FilledTonalButton(feedbackClick {
                        if (!saving) { saving = true; scope.launch {
                            try { save(context, file); saving = false; snack.showSnackbar("已保存到系统相册") }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (failure: Exception) { snack.showSnackbar(failure.message ?: "图片未能保存，请重试") }
                            finally { saving = false }
                        } }
                    }, Modifier.padding(top = 12.dp).testTag("save-profile-image")) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (saving) uiText("profile.saving_image") else uiText("profile.save_to_album"))
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
            }
        }
    }
}
