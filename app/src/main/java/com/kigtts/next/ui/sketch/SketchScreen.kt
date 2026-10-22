package com.kigtts.next.ui.sketch

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.*

@Composable fun SketchScreen(state: SketchState, shareRequest: Int, shareConsumed: () -> Unit, message: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val repository = remember(context.applicationContext) { SketchAlbumRepository(context.applicationContext) }
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val active = LocalPageActive.current; val chrome = LocalSketchChrome.current
    var ready by remember { mutableStateOf(state.documentSize.width > 0) }
    var busy by remember { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var album by rememberSaveable { mutableStateOf(false) }
    val galleryTransition = remember { MutableTransitionState(album) }
    galleryTransition.targetState = album
    val galleryOnScreen = galleryTransition.currentState || galleryTransition.targetState
    var paper by rememberSaveable { mutableStateOf(false) }
    var opening by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = active && ready && !busy
    SideEffect { chrome?.drawing = !galleryOnScreen }
    DisposableEffect(chrome) { onDispose { chrome?.drawing = true } }
    LaunchedEffect(state, repository) {
        if (!ready) {
            try { withContext(Dispatchers.IO) { repository.loadDraft() }?.let(state::restore) }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message("无法恢复上次画板，已打开空白画板") }
            ready = true
        }
        state.startSession()
    }
    LaunchedEffect(background, ready) { if (ready) state.themeBackground(background.toArgb().toLong() and 0xFFFFFFFFL) }
    LaunchedEffect(state.revision, ready) {
        if (ready && state.documentSize.width > 0) SketchDraftWriter.submit(repository, state.document())
    }
    DisposableEffect(state, repository) { onDispose {
        if (state.documentSize.width > 0) SketchDraftWriter.submit(repository, state.document())
    } }
    fun work(operation: suspend () -> Unit) {
        if (!enabled) return
        busy = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { operation() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message(failure.message ?: "操作未完成，请重试") }
            finally { busy = false }
        }
    }
    val save = rememberSketchSavePermission({ val drawing = state.document(); work {
        withContext(Dispatchers.IO) { repository.save(drawing) }; message("已保存到作品相册和系统相册")
    } }, message)
    fun share() { val drawing = state.document(); work {
        val file = withContext(Dispatchers.IO) { exportSketch(context, drawing, false) }
        shareSketchFile(context, file)
    } }
    LaunchedEffect(shareRequest, ready) { if (ready && shareRequest > 0) { share(); shareConsumed() } }
    fun openWork(id: String) { work {
        val drawing = withContext(Dispatchers.IO) { repository.load(id) }
        state.restore(drawing); album = false; opening = null
    } }
    RegisterPageLoading(busy || !ready)
    RegisterPageActions(listOf(
        ToolbarAction("sketch-album-open", "画板作品", Icons.Default.Collections, enabled, preserveColorWhenLocked = busy, click = { album = true }),
        ToolbarAction("sketch-reset-view", "显示全部笔迹", Icons.Default.FitScreen, enabled, click = state::resetViewport),
        ToolbarAction("sketch-paper", "画布背景", Icons.Default.GridOn, enabled, click = { paper = true })))
    RegisterPageBackGuard(active && (busy || galleryOnScreen)) { if (!busy) album = false }
    PredictiveBackHandler(active && (busy || galleryOnScreen) && opening == null && !clear) { progress ->
        try { progress.collect { }; if (!busy) album = false } catch (_: CancellationException) { }
    }
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().background(background).testTag("sketch-workspace")) {
        val landscape = maxWidth > maxHeight
        SketchCanvas(state, enabled && !galleryOnScreen, ready, background.toArgb().toLong() and 0xFFFFFFFFL)
        SketchToolbar(state, save, { clear = true },
            Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (landscape) PaddingValues(end = 12.dp) else PaddingValues(bottom = 16.dp))
                .testTag(if (landscape) "sketch-toolbar-vertical" else "sketch-toolbar-horizontal")
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() } } },
            vertical = landscape, enabled = enabled && !galleryOnScreen)
    }
    SketchGalleryMotion(galleryTransition) {
        SketchGalleryScreen(repository, { id ->
            if (state.strokes.any { !it.erase }) opening = id else openWork(id)
        }, { album = false }, message)
    }
    }
    if (active && paper) SketchBackgroundDialog(state) { paper = false }
    if (active && clear) MotionAlertDialog(onDismissRequest = { clear = false }, title = { Text("清空画板？") },
        text = { Text("当前笔画将被移除。已保存的作品不受影响。") },
        confirmButton = { MotionTextButton(dialogFeedbackClick { state.clear(); clear = false }) { Text("清空") } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { clear = false }) { Text("取消") } })
    if (active && opening != null) MotionAlertDialog(onDismissRequest = { opening = null }, title = { Text("打开这幅作品？") },
        text = { Text("当前未保存的笔画将被替换。") },
        confirmButton = { MotionTextButton(dialogFeedbackClick { opening?.let(::openWork) }, enabled = !busy) { Text("打开") } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { opening = null }, enabled = !busy) { Text("取消") } })
}
