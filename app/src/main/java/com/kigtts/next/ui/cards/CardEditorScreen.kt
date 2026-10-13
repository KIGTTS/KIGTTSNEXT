package com.kigtts.next.ui.cards

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

private val CardDraftSaver = Saver<PolaroidCard, String>(
    save = { PluginJson.encodeToString(it) }, restore = { PluginJson.decodeFromString(it) })

@Composable
fun CardEditorScreen(cards: CardRepository, stickers: StickerRepository, cardId: String, message: (String) -> Unit,
    openLibrary: () -> Unit = {}, openCamera: () -> Unit = {}, cameraImageId: String? = null,
    cameraOriginalImageId: String? = null, cameraFrameStyleId: String? = null, cameraRequestVersion: Long = 0L) {
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val frames by stickers.frameStyles.collectAsStateWithLifecycle()
    val persisted = catalog.cards.firstOrNull { it.id == cardId }
    if (persisted == null) { Text("这张拍立得已移除", Modifier.padding(24.dp)); return }
    var draft by rememberSaveable(cardId, stateSaver = CardDraftSaver) { mutableStateOf(persisted) }
    var baseline by remember(cardId) { mutableStateOf(persisted) }
    var selectedId by rememberSaveable(cardId) { mutableStateOf<String?>(null) }
    var handledCameraVersion by rememberSaveable(cardId) { mutableLongStateOf(0L) }
    var mode by rememberSaveable(cardId) { mutableStateOf(CardEditorMode.CROP) }
    var textDialog by rememberSaveable { mutableStateOf(false) }
    var qrDialog by rememberSaveable { mutableStateOf(false) }
    var qrPayload by rememberSaveable { mutableStateOf("") }
    var showStickers by rememberSaveable { mutableStateOf(false) }
    var paperColor by rememberSaveable { mutableStateOf(false) }
    var textColor by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var overlayImport by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val active = LocalPageActive.current
    val leave = LocalPageBackAction.current
    val dirty = draft != baseline
    val selected = draft.layers.firstOrNull { it.id == selectedId }
    val conflict = persisted != baseline
    LaunchedEffect(persisted) { if (!dirty) { draft = persisted; baseline = persisted } }
    LaunchedEffect(cameraRequestVersion, cameraImageId) {
        if (cameraRequestVersion != 0L && cameraRequestVersion != handledCameraVersion && cameraImageId != null) {
            draft = draft.copy(imageId = cameraImageId, originalImageId = cameraOriginalImageId, photoX = .5, photoY = .5, photoZoom = 1.0,
                frameStyleId = cameraFrameStyleId ?: draft.frameStyleId)
            selectedId = null; mode = CardEditorMode.CROP
            handledCameraVersion = cameraRequestVersion
        }
    }
    fun work(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { operation() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { message(failure.message ?: "操作未完成，请重试") }
            finally { busy = false }
        }
    }
    fun updateLayer(layer: CardLayer) { draft = draft.copy(layers = draft.layers.map { if (it.id == layer.id) layer else it }) }
    fun addLayer(layer: CardLayer) {
        if (draft.layers.size >= 64) { message("一张拍立得最多添加 64 个元素"); return }
        draft = draft.copy(layers = draft.layers + layer); selectedId = layer.id
    }
    val photo = rememberCardPhotoSources({ selection ->
        if (busy) { selection.release(); message("正在处理图片，请稍后重试") }
        else work {
            val image = withContext(Dispatchers.IO) { try { stickers.importImage(selection.uri) } finally { selection.release() } }
            if (overlayImport) addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, width = .32))
            else draft = draft.copy(imageId = image.id, originalImageId = null, photoX = .5, photoY = .5, photoZoom = 1.0)
        }
    }, message)
    val enabled = active && !busy && !photo.processing
    RegisterPageLoading(busy || photo.processing)
    RegisterPageActions(listOf(ToolbarAction("save-card", "保存拍立得", Icons.Default.Save,
        enabled && dirty && !conflict && draft.title.isNotBlank(), preserveColorWhenLocked = busy, click = {
            val candidate = draft; val previous = baseline
            work {
                val saved = withContext(Dispatchers.IO) { synchronized(stickers) {
                    check(cards.cards.value.firstOrNull { it.id == cardId } == previous) { "拍立得已在其它位置更新，请重新打开后保存" }
                    cards.update(candidate.copy(title = candidate.title.trim()))
                } }
                baseline = saved; draft = saved; message("已保存拍立得"); leave?.invoke()
            }
        })))
    RegisterPageBackGuard(active && dirty) { discard = true }
    PredictiveBackHandler(active && dirty && !discard && !showStickers && !paperColor && !textColor && !textDialog && !qrDialog) { events ->
        try { events.collect { }; discard = true } catch (_: CancellationException) { }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("card-editor"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (conflict) Text("此拍立得已在其它位置更新，请返回后重新打开", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            PolaroidCanvas(draft, stickers::imageFile, Modifier.width(minOf(maxWidth, 340.dp)),
                if (mode == CardEditorMode.STICKERS) selectedId else null, enabled && mode == CardEditorMode.STICKERS,
                { selectedId = it }, ::updateLayer, photoEditable = enabled && mode == CardEditorMode.CROP,
                onPhotoChange = { draft = it }, frame = frames.firstOrNull { it.id == draft.frameStyleId })
        }
        CardEditorFields(draft, enabled, { draft = it },
            camera = { overlayImport = false; openCamera() }, gallery = { overlayImport = false; photo.gallery() },
            addText = { selectedId = null; textDialog = true }, addSticker = { showStickers = true },
            addImage = { overlayImport = true; photo.gallery() }, addQrCode = { qrDialog = true }, frameColor = { paperColor = true },
            selected = selected, editText = { textDialog = true }, layerColor = { textColor = true },
            deleteLayer = { draft = draft.copy(layers = draft.layers.filterNot { it.id == selectedId }); selectedId = null },
            forwardLayer = { selected?.let { layer -> draft = draft.copy(layers = draft.layers.filterNot { it.id == layer.id } + layer) } },
            mode = mode, selectMode = { mode = it }, frames = frames, openLibrary = openLibrary)
    }
    if (active && textDialog) CardNameDialog(if (selected?.kind == CardLayerKind.TEXT) "编辑文字" else "添加文字",
        selected?.takeIf { it.kind == CardLayerKind.TEXT }?.text.orEmpty(), { textDialog = false }, { text ->
            textDialog = false
            if (selected?.kind == CardLayerKind.TEXT) updateLayer(selected.copy(text = text))
            else addLayer(CardLayer(kind = CardLayerKind.TEXT, text = text, width = .07))
        }, multiline = true, maximum = 2048)
    if (active && qrDialog) CardNameDialog("添加二维码", qrPayload, { qrDialog = false }, { text ->
        qrDialog = false; qrPayload = text
        work {
            val image = withContext(Dispatchers.IO) { stickers.createQrCode(text) }
            addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, text = text, width = .32))
        }
    }, multiline = true, maximum = 2048, trimInput = false, inputLabel = "二维码内容")
    if (active && showStickers) StickerPickerSheet(catalog.libraries, catalog.items, stickers::imageFile,
        { item -> showStickers = false; addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = item.imageId, width = .32)) },
        { showStickers = false; overlayImport = true; photo.gallery() }, { showStickers = false },
        { showStickers = false; openLibrary() })
    if (active && paperColor) ColorPickerDialog(draft.frameColor, { color -> draft = draft.copy(frameColor = color); paperColor = false },
        { paperColor = false }, title = "纸框颜色")
    if (active && textColor && selected?.kind == CardLayerKind.TEXT) ColorPickerDialog(selected.color, { color ->
        updateLayer(selected.copy(color = color)); textColor = false
    }, { textColor = false }, title = "文字颜色")
    if (active && discard) MotionAlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { MotionTextButton(dialogFeedbackClick { draft = baseline; discard = false; leave?.invoke() }) { Text("放弃") } },
        dismissButton = { MotionTextButton(dialogFeedbackClick { discard = false }) { Text("继续编辑") } })
}
