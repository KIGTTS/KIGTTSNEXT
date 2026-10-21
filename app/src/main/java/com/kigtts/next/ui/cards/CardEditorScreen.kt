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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    cameraOriginalImageId: String? = null, cameraFrameStyleId: String? = null, cameraRequestVersion: Long = 0L,
    cameraPhotoX: Double = .5, cameraPhotoY: Double = .5, cameraPhotoZoom: Double = 1.0) {
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val frames by stickers.frameStyles.collectAsStateWithLifecycle()
    val readOnly by stickers.readOnlyCards.collectAsStateWithLifecycle()
    val persisted = catalog.cards.firstOrNull { it.id == cardId }
    if (persisted == null) { Text("这张拍立得已移除", Modifier.padding(24.dp)); return }
    if (cardId in readOnly) {
        Text("接收的照片只能查看和分享", Modifier.padding(24.dp))
        return
    }
    var draft by rememberSaveable(cardId, stateSaver = CardDraftSaver) { mutableStateOf(persisted) }
    var baseline by rememberSaveable(cardId, stateSaver = CardDraftSaver) { mutableStateOf(persisted) }
    var selectedId by rememberSaveable(cardId) { mutableStateOf<String?>(null) }
    var handledCameraVersion by rememberSaveable(cardId) { mutableLongStateOf(0L) }
    var mode by rememberSaveable(cardId) { mutableStateOf(CardEditorMode.CROP) }
    var textEditing by rememberSaveable { mutableStateOf(false) }
    var qrDialog by rememberSaveable { mutableStateOf(false) }
    var qrPayload by rememberSaveable { mutableStateOf("") }
    var stickerLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var paperColor by rememberSaveable { mutableStateOf(false) }
    var textColor by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable(cardId) { mutableStateOf(false) }
    var savedReturn by rememberSaveable(cardId) { mutableStateOf(false) }
    var overlayImport by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = LocalPageActive.current
    val currentActive = rememberUpdatedState(active)
    val leave = LocalPageBackAction.current
    val dirty = draft != baseline
    val selected = draft.layers.firstOrNull { it.id == selectedId }
    val conflict = persisted != baseline
    LaunchedEffect(persisted) { if (!dirty) { draft = persisted; baseline = persisted } }
    LaunchedEffect(cameraRequestVersion, cameraImageId) {
        if (cameraRequestVersion != 0L && cameraRequestVersion != handledCameraVersion && cameraImageId != null) {
            draft = draft.copy(imageId = cameraImageId, originalImageId = cameraOriginalImageId, photoX = cameraPhotoX, photoY = cameraPhotoY, photoZoom = cameraPhotoZoom,
                frameStyleId = cameraFrameStyleId ?: draft.frameStyleId)
            selectedId = null; mode = CardEditorMode.CROP
            handledCameraVersion = cameraRequestVersion
        }
    }
    fun work(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
    fun putPhotoField(layer: CardLayer) {
        if (draft.layers.any { it.id == layer.id }) updateLayer(layer)
        else if (draft.layers.size < 64) draft = draft.copy(layers = draft.layers + layer)
        else message("一张拍立得最多添加 64 个元素")
    }
    val photo = rememberCardPhotoSources({ selection ->
        if (busy) { selection.release(); message("正在处理图片，请稍后重试") }
        else work {
            val image = withContext(Dispatchers.IO) { try { stickers.importImage(selection.uri) } finally { selection.release() } }
            if (overlayImport) addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, width = .32))
            else draft = draft.copy(imageId = image.id, originalImageId = null, photoX = .5, photoY = .5, photoZoom = 1.0)
        }
    }, message)
    val enabled = active && !busy && !photo.processing && !savedReturn
    RegisterPageLoading(busy || photo.processing)
    fun saveDraft() {
        if (!enabled || !dirty || conflict) return
        val candidate = draft; val previous = baseline
        work {
            val saved = withContext(Dispatchers.IO) { synchronized(stickers) {
                check(cards.cards.value.firstOrNull { it.id == cardId } == previous) { "拍立得已在其它位置更新，请重新打开后保存" }
                cards.update(candidate.copy(title = candidate.title.trim(),
                    layers = candidate.layers.filterNot { it.kind == CardLayerKind.TEXT && it.text.isBlank() }))
            } }
            baseline = saved; draft = saved; savedReturn = true; message("已保存拍立得")
        }
    }
    LaunchedEffect(active, savedReturn, discard) {
        if (active && savedReturn && !discard) { savedReturn = false; leave?.invoke() }
    }
    RegisterPageActions(listOf(ToolbarAction("save-card", "保存拍立得", Icons.Default.Save,
        enabled && dirty && !conflict, preserveColorWhenLocked = busy, click = { saveDraft() })))
    fun cancelAdjustment() { selectedId = null; textEditing = false; focus.clearFocus(); keyboard?.hide() }
    fun backFromEditor() { if (selectedId != null || textEditing) cancelAdjustment() else discard = true }
    RegisterPageBackGuard(active && (dirty || selectedId != null || textEditing || savedReturn || busy)) { if (!savedReturn && !busy) backFromEditor() }
    PredictiveBackHandler(active && (dirty || selectedId != null || textEditing || savedReturn || busy) && !discard && !paperColor && !textColor && !qrDialog) { events ->
        try { events.collect { }; if (!savedReturn && !busy) backFromEditor() } catch (_: CancellationException) { }
    }
    CardEditorPanelScaffold(polaroidFormat(draft, frames.firstOrNull { it.id == draft.frameStyleId }).geometry.aspectRatio.toFloat(),
        enabled, textEditing, ::cancelAdjustment, preview = { paperModifier ->
            PolaroidCanvas(draft, stickers::imageFile, paperModifier,
                if (mode == CardEditorMode.STICKERS) selectedId else null, enabled && mode == CardEditorMode.STICKERS,
                { if (it == null) cancelAdjustment() else { selectedId = it; if (draft.layers.none { layer -> layer.id == it && layer.kind == CardLayerKind.TEXT }) textEditing = false } },
                ::updateLayer, photoEditable = enabled && mode == CardEditorMode.CROP,
                onPhotoChange = { draft = it }, frame = frames.firstOrNull { it.id == draft.frameStyleId })
        }, tools = {
        if (conflict) Text("此拍立得已在其它位置更新，请返回后重新打开", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        CardEditorFields(draft, enabled, { draft = it },
            camera = { overlayImport = false; openCamera() }, gallery = { overlayImport = false; photo.gallery() },
            addText = { addLayer(CardLayer(kind = CardLayerKind.TEXT, text = "文字", width = .07)); textEditing = true }, addSticker = { cancelAdjustment() },
            addImage = { overlayImport = true; photo.gallery() }, addQrCode = { qrDialog = true }, frameColor = { paperColor = true },
            selected = selected, editText = { textEditing = true }, layerColor = { textColor = true },
            deleteLayer = { draft = draft.copy(layers = draft.layers.filterNot { it.id == selectedId }); selectedId = null },
            forwardLayer = { selected?.let { layer -> draft = draft.copy(layers = draft.layers.filterNot { it.id == layer.id } + layer) } },
            mode = mode, selectMode = { mode = it; cancelAdjustment() }, frames = frames, openLibrary = openLibrary,
            textEditing = textEditing, updateTextLayer = ::updateLayer,
            finishText = { textEditing = false; focus.clearFocus(); keyboard?.hide() }, showTabs = false,
            stickerLibrary = {
                StickerLibraryPanel(catalog.libraries, catalog.items, stickerLibraryId, { stickerLibraryId = it },
                    stickers::imageFile, enabled, addLibrary = openLibrary, editLibrary = {},
                    selectItem = { addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = it.imageId, width = .32)) },
                    itemMenu = {}, modifier = Modifier.fillMaxWidth().height(240.dp), showLibraryActions = false)
            }, designTools = { CardDesignTools(cards, draft, enabled, { draft = it }, message) },
            captureFields = { CardPhotoFieldTools(cards, draft, enabled, ::putPhotoField, { id -> draft = draft.copy(layers = draft.layers.filterNot { it.id == id }) }, message) })
    }, categories = { compact, openTools -> CardEditorTabs(mode, { mode = it; cancelAdjustment(); openTools() }, compact) },
        imageFile = stickers::imageFile, insertElement = { layer ->
            addLayer(layer); mode = CardEditorMode.STICKERS; textEditing = false
        })
    if (active && qrDialog) CardNameDialog("添加二维码", qrPayload, { qrDialog = false }, { text ->
        qrDialog = false; qrPayload = text
        work {
            val image = withContext(Dispatchers.IO) { stickers.createQrCode(text) }
            addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, text = text, width = .32))
        }
    }, multiline = true, maximum = 2048, trimInput = false, inputLabel = "二维码内容")
    if (active && paperColor) ColorPickerDialog(draft.frameColor, { color -> draft = draft.copy(frameColor = color); paperColor = false },
        { paperColor = false }, title = "纸框颜色")
    if (active && textColor && selected?.kind == CardLayerKind.TEXT) ColorPickerDialog(selected.color, { color ->
        updateLayer(selected.copy(color = color)); textColor = false
    }, { textColor = false }, title = "文字颜色")
    if (active && discard) SaveChangesDialog({ _ -> saveDraft() },
        { draft = baseline; discard = false; leave?.invoke() }, { discard = false },
        canSave = enabled && dirty && !conflict, busy = busy || savedReturn,
        error = if (conflict) "这张照片已更新，请重新打开。" else null,
        completed = if (savedReturn) ({
            if (currentActive.value && savedReturn) { savedReturn = false; discard = false; leave?.invoke() }
        }) else null)
}
