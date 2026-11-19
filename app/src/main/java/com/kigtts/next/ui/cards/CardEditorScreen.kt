package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.rememberUiCopy

import com.kigtts.next.i18n.uiText

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
import com.kigtts.next.identity.IdentityCard
import com.kigtts.next.ui.identity.PersonalCardOverlay
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
    cameraPhotoX: Double = .5, cameraPhotoY: Double = .5, cameraPhotoZoom: Double = 1.0,
    identity: IdentityCard? = null, personalDesign: PolaroidCard? = null,
    savePersonal: ((PolaroidCard, IdentityCard) -> PolaroidCard)? = null,
    selectPhoto: (((PolaroidCard) -> Unit) -> Unit)? = null, cameraPhotoStyle: PolaroidPhotoStyle? = null, onSaved: (suspend (PolaroidCard) -> Unit)? = null) {
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val identityFrames by stickers.identityFrames.items.collectAsStateWithLifecycle()
    val privateDesigns by stickers.identityDesigns.items.collectAsStateWithLifecycle()
    val frames by stickers.frameStyles.collectAsStateWithLifecycle()
    val readOnly by stickers.readOnlyCards.collectAsStateWithLifecycle()
    val persisted = if (identity != null) personalDesign ?: privateDesigns.firstOrNull { it.id == cardId } else catalog.cards.firstOrNull { it.id == cardId }
    if (persisted == null) { Text(uiText("photo.removed"), Modifier.padding(24.dp)); return }
    if (cardId in readOnly) {
        Text(uiText("photo.received_read_only"), Modifier.padding(24.dp))
        return
    }
    var draft by rememberSaveable(cardId, stateSaver = CardDraftSaver) { mutableStateOf(persisted) }
    var baseline by rememberSaveable(cardId, stateSaver = CardDraftSaver) { mutableStateOf(persisted) }
    var personalDraft by remember(identity?.id) { mutableStateOf(identity) }
    var personalBaseline by remember(identity?.id) { mutableStateOf(identity) }
    var maskColor by remember { mutableStateOf(false) }
    val savedMessage = uiText(if (identity == null) "photo.saved" else "profile.card_saved")
    val copy = rememberUiCopy()
    var selectedId by rememberSaveable(cardId) { mutableStateOf<String?>(null) }
    var handledCameraVersion by rememberSaveable(cardId) { mutableLongStateOf(0L) }
    var mode by rememberSaveable(cardId) { mutableStateOf(CardEditorMode.CROP) }
    var generatorLayer by remember { mutableStateOf<CardLayer?>(null) }
    val generators = remember(stickers) { com.kigtts.next.cards.generators.GeneratedStickers(stickers) }
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
    var cropOpen by rememberSaveable(cardId) { mutableStateOf(false) }
    val viewport = remember(cardId, draft.frameStyleId) { PhotoEditorViewport() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = LocalPageActive.current
    val currentActive = rememberUpdatedState(active)
    val leave = LocalPageBackAction.current
    val dirty = draft != baseline || personalDraft != personalBaseline
    val backgroundPainter = personalDraft?.let { com.kigtts.next.ui.identity.rememberIdentityPainter(draft, it, stickers) }
    val selected = draft.layers.firstOrNull { it.id == selectedId }
    val conflict = persisted != baseline
    LaunchedEffect(persisted) { if (!dirty) { draft = persisted; baseline = persisted } }
    LaunchedEffect(cameraRequestVersion, cameraImageId) {
        if (cameraRequestVersion != 0L && cameraRequestVersion != handledCameraVersion && cameraImageId != null) {
            draft = draft.copy(imageId = cameraImageId, originalImageId = cameraOriginalImageId, photoStyle = cameraPhotoStyle, photoX = cameraPhotoX, photoY = cameraPhotoY, photoZoom = cameraPhotoZoom,
                frameStyleId = if (persisted.frameStyleId == "identity.banner") "identity.banner" else cameraFrameStyleId ?: draft.frameStyleId)
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
        if (draft.layers.size >= 64) { message(copy("photo.max_layers")); return }
        draft = draft.copy(layers = draft.layers + layer); selectedId = layer.id
    }
    fun putPhotoField(layer: CardLayer) {
        if (draft.layers.any { it.id == layer.id }) updateLayer(layer)
        else if (draft.layers.size < 64) draft = draft.copy(layers = draft.layers + layer)
        else message(copy("photo.max_layers"))
    }
    val photo = rememberCardPhotoSources({ selection ->
        if (busy) { selection.release(); message(copy("photo.processing_wait")) }
        else work {
            val image = withContext(Dispatchers.IO) { try { stickers.importImage(selection.uri) } finally { selection.release() } }
            if (overlayImport) addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, width = .32))
            else draft = draft.copy(imageId = image.id, originalImageId = null, photoStyle = null, photoX = .5, photoY = .5, photoZoom = 1.0,
                frameStyleId = if (identity == null) PhotoFrameSpec(image.width, image.height, PhotoFrameSpec.from(draft.frameStyleId)?.style ?: "none").id else draft.frameStyleId)
        }
    }, message)
    val enabled = active && !busy && !photo.processing && !savedReturn
    RegisterPageLoading(busy || photo.processing)
    fun saveDraft() {
        if (!enabled || !dirty || conflict) return
        val candidate = draft; val previous = baseline
        work {
            val saved = withContext(Dispatchers.IO) { synchronized(stickers) {
                check((if (identity != null) stickers.identityDesigns.get(cardId) else cards.cards.value.firstOrNull { it.id == cardId }) == previous) { "照片已在其它位置更新，请重新打开后保存" }
                val clean = candidate.copy(title = candidate.title.trim(), layers = candidate.layers.filterNot { it.kind == CardLayerKind.TEXT && it.text.isBlank() })
                if (personalDraft != null && savePersonal != null) savePersonal(clean, requireNotNull(personalDraft)) else { cards.documents.update(clean) }
            } }
            val embedded = identity == null && cards.documents.isEmbedded(cardId)
            if (onSaved != null || embedded) {
                try {
                    if (onSaved != null) onSaved(saved) else cards.documents.saveEmbedded(saved)
                }
                catch (failure: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (cards.cards.value.any { it == saved }) cards.documents.update(previous)
                    }
                    throw failure
                }
                message(savedMessage)
                if (onSaved == null) leave?.invoke()
                return@work
            }
            baseline = saved; draft = saved; personalBaseline = personalDraft; savedReturn = true; message(savedMessage)
        }
    }
    LaunchedEffect(active, savedReturn, discard) {
        if (active && savedReturn && !discard) { savedReturn = false; leave?.invoke() }
    }
    RegisterPageActions(listOf(ToolbarAction("save-card", uiText("action.save"), Icons.Default.Save,
        enabled && dirty && !conflict, preserveColorWhenLocked = busy, click = { saveDraft() })))
    fun cancelAdjustment() { selectedId = null; textEditing = false; focus.clearFocus(); keyboard?.hide() }
    fun backFromEditor() { if (selectedId != null || textEditing) cancelAdjustment() else discard = true }
    val managedDraft = identity == null && cards.documents.isEmbedded(cardId)
    fun requestClose() {
        if (savedReturn || busy) return
        if (!dirty && selectedId == null && !textEditing && managedDraft) work {
            cards.documents.discardEmbedded(cardId); leave?.invoke()
        } else backFromEditor()
    }
    RegisterPageBackGuard(active && (managedDraft || dirty || selectedId != null || textEditing || savedReturn || busy)) { requestClose() }
    PredictiveBackHandler(active && (managedDraft || dirty || selectedId != null || textEditing || savedReturn || busy) && !discard && !paperColor && !textColor && !qrDialog) { events ->
        try { events.collect { }; requestClose() } catch (_: CancellationException) { }
    }
    CardEditorPanelScaffold(polaroidPaperGeometry(draft, stickers.frameStyle(draft)).aspectRatio.toFloat(),
        enabled, textEditing, ::cancelAdjustment, preview = { paperModifier ->
            Box(paperModifier.aspectRatio(polaroidPaperGeometry(draft, stickers.frameStyle(draft)).aspectRatio.toFloat())) {
            PolaroidCanvas(draft, stickers::imageFile, Modifier.fillMaxSize(),
                if (mode == CardEditorMode.STICKERS) selectedId else null, enabled && mode == CardEditorMode.STICKERS,
                { if (it == null) cancelAdjustment() else { selectedId = it; if (draft.layers.none { layer -> layer.id == it && layer.kind == CardLayerKind.TEXT }) textEditing = false } },
                ::updateLayer, photoEditable = identity != null && enabled && mode == CardEditorMode.CROP,
                onPhotoChange = { draft = it }, frame = stickers.frameStyle(draft),
                photoFacts = if (identity == null) remember(stickers, draft.id) { stickers.designs.facts(draft.id) } else null,
                configurableLayer = { layer -> generators.contains(layer.imageId) },
                configureLayer = { layer -> if (generators.get(layer.imageId) != null) generatorLayer = layer },
                removeLayer = { id -> draft = draft.copy(layers = draft.layers.filterNot { it.id == id }); selectedId = null },
                photoContent = if (identity != null) ({ com.kigtts.next.ui.identity.IdentityLiveBackground(draft, backgroundPainter) }) else null)
            personalDraft?.let { PersonalCardOverlay(it, stickers, Modifier.matchParentSize(), interactive = false,
                backgroundColor = draft.frameColor, hasImage = draft.imageId != null, backgroundInk = backgroundPainter?.ink) }
            }
        }, tools = {
        if (conflict) Text(uiText("photo.stale_edit"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        CardEditorFields(draft, enabled, { draft = it },
            openCrop = { cropOpen = true },
            addText = { addLayer(CardLayer(kind = CardLayerKind.TEXT, text = "文字", width = .07)); textEditing = true }, addSticker = { cancelAdjustment() },
            addImage = { overlayImport = true; photo.gallery() }, addQrCode = { qrDialog = true }, frameColor = { paperColor = true },
            selected = selected, editText = { textEditing = true }, layerColor = { textColor = true },
            deleteLayer = { draft = draft.copy(layers = draft.layers.filterNot { it.id == selectedId }); selectedId = null },
            forwardLayer = { selected?.let { layer -> draft = draft.copy(layers = draft.layers.filterNot { it.id == layer.id } + layer) } },
            mode = mode, selectMode = { mode = it; cancelAdjustment() }, frames = if (persisted.frameStyleId == "identity.banner") frames.filter { it.id == "identity.banner" } else frames, openLibrary = openLibrary,
            textEditing = textEditing, updateTextLayer = ::updateLayer,
            finishText = { textEditing = false; focus.clearFocus(); keyboard?.hide() }, showTabs = false,
            stickerLibrary = {
                StickerLibraryPanel(catalog.libraries, catalog.items, stickerLibraryId, { stickerLibraryId = it },
                    stickers::imageFile, enabled, addLibrary = openLibrary, editLibrary = {},
                    selectItem = { item -> work {
                        addLayer(preparePhotoSticker(CardLayer(kind = CardLayerKind.IMAGE, imageId = item.imageId, width = .32), draft, cards, stickers))
                    } },
                    itemMenu = {}, modifier = Modifier.fillMaxWidth().height(240.dp), showLibraryActions = false)
            }, designTools = { CardDesignTools(cards, draft, enabled, { draft = it }, message) },
            backgroundTools = personalDraft?.let { person -> {
                com.kigtts.next.ui.identity.IdentityBackgroundTools(draft, person, enabled, { draft = it }, { personalDraft = it },
                    { overlayImport = false; photo.gallery() }, { selectPhoto?.invoke { chosen -> draft = draft.copy(imageId = chosen.imageId, originalImageId = null, photoStyle = chosen.photoStyle, photoX = .5, photoY = .5, photoZoom = 1.0) } },
                    { paperColor = true }, { maskColor = true }, identityFrames)
            } }, captureFields = { CardPhotoFieldTools(cards, draft, enabled, ::putPhotoField, { id -> draft = draft.copy(layers = draft.layers.filterNot { it.id == id }) }, message) },
            filterTools = { CardPhotoFilterTools(draft, stickers, enabled, { draft = it }, message) })
    }, categories = { compact, openTools -> CardEditorTabs(mode, { mode = it; cancelAdjustment(); openTools() }, compact, personal = identity != null) },
        imageFile = stickers::imageFile, insertElement = { layer ->
            work { addLayer(preparePhotoSticker(layer, draft, cards, stickers)); mode = CardEditorMode.STICKERS; textEditing = false }
        }, avoidTools = identity != null, photoViewport = viewport.takeIf { identity == null },
        transformPhoto = enabled && selected == null)
    if (active && cropOpen && identity == null) {
        val id = draft.imageId
        if (id != null) {
            val source = rememberCardImage(stickers.imageFile(id), 2048)
            val styled = rememberCardPhotoStyle(source, id, draft.photoStyle, stickers::imageFile)
            val spec = remember(draft.frameStyleId) { PhotoFrameSpec.from(draft.frameStyleId) }
            PhotoCropDialog(styled ?: source, spec?.crop ?: PhotoCrop.Full, { cropOpen = false }) { crop, width, height ->
                if (crop != (spec?.crop ?: PhotoCrop.Full)) {
                    val (left, top, right, bottom) = crop.pixels(width, height)
                    val style = spec?.style ?: draft.frameStyleId
                    draft = draft.copy(frameStyleId = PhotoFrameSpec(right - left, bottom - top, style,
                        crop.takeUnless { it == PhotoCrop.Full }).id, photoX = .5, photoY = .5, photoZoom = 1.0,
                        photoBackdrop = PhotoBackdropMode.GRAY)
                }
                cropOpen = false
            }
        }
    }
    if (active) generatorLayer?.let { layer -> generators.get(layer.imageId)?.let { source ->
        GeneratedStickerDialog(layer, source, stickers, { generatorLayer = null }, ::updateLayer)
    } }
    if (active && qrDialog) CardNameDialog("添加二维码", qrPayload, { qrDialog = false }, { text ->
        qrDialog = false; qrPayload = text
        work {
            val image = withContext(Dispatchers.IO) { stickers.createQrCode(text) }
            addLayer(CardLayer(kind = CardLayerKind.IMAGE, imageId = image.id, text = text, width = .32))
        }
    }, multiline = true, maximum = 2048, trimInput = false, inputLabel = "二维码内容")
    if (active && paperColor) ColorPickerDialog(draft.frameColor, { color -> draft = draft.copy(frameColor = color); paperColor = false },
        { paperColor = false }, title = uiText(if (identity == null) "photo.frame_color" else "profile.background_color"))
    if (active && maskColor && personalDraft != null) ColorPickerDialog(requireNotNull(personalDraft).gradientColor, { color ->
        personalDraft = personalDraft?.copy(gradientColor = color); maskColor = false
    }, { maskColor = false }, title = uiText("photo.gradient_color"))
    if (active && textColor && selected?.kind == CardLayerKind.TEXT) ColorPickerDialog(selected.color, { color ->
        updateLayer(selected.copy(color = color)); textColor = false
    }, { textColor = false }, title = uiText("photo.text_color"))
    if (active && discard) SaveChangesDialog({ _ -> saveDraft() },
        {
            if (managedDraft) work { cards.documents.discardEmbedded(cardId); leave?.invoke() }
            else { draft = baseline; discard = false; leave?.invoke() }
        }, { discard = false },
        canSave = enabled && dirty && !conflict, busy = busy || savedReturn,
        error = if (conflict) "这张照片已更新，请重新打开。" else null,
        completed = if (savedReturn) ({
            if (currentActive.value && savedReturn) { savedReturn = false; discard = false; leave?.invoke() }
        }) else null)
}
