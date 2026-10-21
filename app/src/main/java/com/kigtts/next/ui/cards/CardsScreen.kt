package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.cards.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.PolaroidCard

@Composable
fun CardsScreen(cards: CardRepository, stickers: StickerRepository, openPreview: (String) -> Unit,
    openEditor: (String) -> Unit, message: (String) -> Unit, onQuickImage: (String, String) -> Unit = { _, _ -> },
    openCamera: () -> Unit = {}, requestedCardId: String? = null, requestVersion: Long = 0L,
    onAlbumSelected: (String) -> Unit = {}, onPreviewRequestHandled: (Long) -> Unit = {},
    openRequestedPreview: Boolean = true, openSystemCamera: () -> Unit = openCamera) {
    val context = LocalContext.current
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val frames by stickers.frameStyles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val editor = rememberUpdatedState(openEditor); val feedback = rememberUpdatedState(message); val quick = rememberUpdatedState(onQuickImage)
    val actions = remember(cards, stickers) { CardWorkspaceActions(context, cards, stickers, scope,
        { editor.value(it) }, { feedback.value(it) }, { id, title -> quick.value(id, title) }) }
    val preferences = remember(context) { context.getSharedPreferences("cards-view", 0) }
    var discovery by remember { mutableStateOf(preferences.getBoolean("face-discovery", false)) }
    var mode by rememberSaveable { mutableStateOf(PolaroidBrowseMode.STACK) }
    var columnLevel by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlbum by rememberSaveable { mutableStateOf(preferences.getString("album", CardRepository.DEFAULT_ALBUM) ?: CardRepository.DEFAULT_ALBUM) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var positionRequest by rememberSaveable { mutableLongStateOf(0L) }
    var albumChangeRequest by rememberSaveable { mutableLongStateOf(0L) }
    var pendingPosition by remember { mutableStateOf<Long?>(null) }
    val management = rememberCardManagementState()
    val albumSheet = rememberCardAlbumSheet()
    val expanded = albumSheet.targetValue > 0
    RegisterCardAlbumExpanded(expanded, albumSheet.targetValue)
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRequest by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var removeCard by remember { mutableStateOf<PolaroidCard?>(null) }
    var moveCard by remember { mutableStateOf<PolaroidCard?>(null) }
    var createAlbum by remember { mutableStateOf(false) }
    var renameAlbum by remember { mutableStateOf<String?>(null) }
    var removeAlbum by remember { mutableStateOf<String?>(null) }
    val albums = catalog.albums
    val ordered = remember(catalog.cards, albums) { orderedAlbumCards(albums, catalog.cards) }
    val currentAlbum = albums.firstOrNull { it.id == selectedAlbum }
    val albumPhotos = remember(currentAlbum, catalog.cards) {
        val byId = catalog.cards.associateBy { it.id }
        currentAlbum?.cardIds?.mapNotNull(byId::get).orEmpty()
    }
    val active = LocalPageActive.current
    fun applyFocus(card: PolaroidCard) {
        focusedId = card.id
        albumContaining(albums, card.id)?.let { selectedAlbum = it.id }
    }
    fun selectPhoto(card: PolaroidCard) {
        if (pendingPosition == null) applyFocus(card)
    }
    fun selectAlbum(id: String) {
        if (id != selectedAlbum) albumChangeRequest++
        positionRequest++
        pendingPosition = positionRequest
        selectedAlbum = id
        focusedId = albums.firstOrNull { it.id == id }?.cardIds?.firstOrNull()
        if (focusedId == null) pendingPosition = null
    }
    fun choosePhoto(card: PolaroidCard) { positionRequest++; pendingPosition = positionRequest; applyFocus(card) }
    fun managePhotos(card: PolaroidCard) {
        applyFocus(card); previewId = null; pendingPosition = null
        management.photos(albumContaining(albums, card.id)?.id ?: selectedAlbum, card.id, mode == PolaroidBrowseMode.GRID)
    }
    LaunchedEffect(selectedAlbum, active) {
        if (active) {
            preferences.edit().putString("album", selectedAlbum).apply()
            onAlbumSelected(selectedAlbum)
        }
    }
    LaunchedEffect(catalog.cards, albums) {
        if (previewId != null && catalog.cards.none { it.id == previewId }) previewId = null
        removeCard = removeCard?.let { pending -> catalog.cards.firstOrNull { it.id == pending.id } }
        moveCard = moveCard?.let { pending -> catalog.cards.firstOrNull { it.id == pending.id } }
        if (albums.none { it.id == selectedAlbum }) selectedAlbum = albums.firstOrNull()?.id ?: CardRepository.DEFAULT_ALBUM
        if (focusedId == null || catalog.cards.none { it.id == focusedId })
            focusedId = albums.firstOrNull { it.id == selectedAlbum }?.cardIds?.firstOrNull()
        albumContaining(albums, focusedId)?.let { selectedAlbum = it.id }
    }
    LaunchedEffect(cards) {
        try { cards.migrateLegacy() } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { message("无法读取旧拍立得，请重试") }
    }
    LaunchedEffect(requestVersion, requestedCardId, active) {
        if (active && requestedCardId != null && requestVersion != handledRequest) {
            val card = catalog.cards.firstOrNull { it.id == requestedCardId }
            if (card != null) { choosePhoto(card); previewId = card.id.takeIf { openRequestedPreview } } else message("这张拍立得已移除")
            handledRequest = requestVersion; onPreviewRequestHandled(requestVersion)
        }
    }
    val enabled = active && !actions.busy
    RegisterPageLoading(actions.busy)
    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().clipToBounds().testTag("polaroid-home")) {
        val landscape = LocalCardWindowLandscape.current ?: (maxWidth > maxHeight)
        val minimumColumns = if (landscape) 4 else 2
        val maximumColumns = minimumColumns + 2
        val columns = minimumColumns + columnLevel.coerceIn(0, 2)
        if (!management.active) RegisterPageActions(buildList {
            add(ToolbarAction("cards-camera", "拍照，长按使用系统相机", Icons.Default.PhotoCamera, enabled,
                longClick = openSystemCamera, click = openCamera))
            add(ToolbarAction("cards-browse-mode", if (mode == PolaroidBrowseMode.GRID) "逐张查看" else "宫格查看",
                if (mode == PolaroidBrowseMode.GRID) Icons.Default.ViewCarousel else Icons.Default.GridView, enabled,
                click = { mode = if (mode == PolaroidBrowseMode.GRID) PolaroidBrowseMode.STACK else PolaroidBrowseMode.GRID }))
            if (mode == PolaroidBrowseMode.GRID) add(ToolbarAction("cards-columns",
                if (columns < maximumColumns) "宫格列数：" + columns + "，点按缩小" else "相册总览",
                if (columns < maximumColumns) Icons.Default.ViewWeek else Icons.Default.PhotoLibrary, enabled,
                click = { if (columns < maximumColumns) columnLevel++ else mode = PolaroidBrowseMode.ALBUMS }))
            add(ToolbarAction("face-discovery", "面对面发现", Icons.Default.Radar, active, checked = discovery, click = {
                discovery = !discovery; preferences.edit().putBoolean("face-discovery", discovery).apply()
                message(if (discovery) "已预留面对面发现，暂不搜索附近设备" else "已关闭面对面发现")
            }))
        }, primaryCount = 4)

        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                CardManagementTransition(management.active, browser = {
                PolaroidBrowser(if (albumPhotos.isEmpty() && focusedId == null) emptyList() else ordered,
                    mode, focusedId ?: albumPhotos.firstOrNull()?.id, stickers::imageFile, frames, enabled && !management.active, { mode = it }, ::selectPhoto,
                    { applyFocus(it); previewId = it.id }, { openEditor(it.id) }, Modifier.fillMaxSize(),
                    gridColumns = columns, changeColumns = { columnLevel = (it - minimumColumns).coerceIn(0, 2) }, gridCards = albumPhotos, positionRequest = positionRequest,
                    onPositioned = { version ->
                        if (pendingPosition == version) pendingPosition = null
                    }, albums = albums, albumCards = ordered, chooseAlbum = ::selectAlbum, selectedAlbumId = selectedAlbum,
                    wideLayout = landscape, albumChangeRequest = albumChangeRequest, onManage = ::managePhotos,
                    onManageAlbum = { management.albums(it) })
                }, manager = {
                    CardManagementPane(management, cards, stickers, catalog, frames, actions, enabled, columns,
                        moved = { ids, target ->
                            selectedAlbum = target; focusedId = ids.firstOrNull(); positionRequest++; pendingPosition = positionRequest
                        }, edit = openEditor)
                })
            }
        }
        val dock: @Composable () -> Unit = {
            CardAlbumDock(albums, selectedAlbum, albumPhotos, focusedId, stickers::imageFile, enabled && !management.active, landscape,
                expanded, {}, ::selectAlbum, ::choosePhoto, { createAlbum = true },
                { renameAlbum = selectedAlbum }, { removeAlbum = selectedAlbum }, Modifier.padding(8.dp),
                manage = { management.albums(selectedAlbum) }, manageAlbum = { management.albums(it) }, sheet = albumSheet)
        }
        CompositionLocalProvider(LocalCardWindowLandscape provides landscape) {
            CardBrowsingLayout(landscape, body, dock)
        }
    }
    CardManagementReturn(management, active)
    if (active) {
        catalog.cards.firstOrNull { it.id == previewId }?.let { card ->
            CardPreviewDialog(card, stickers::imageFile, frames.firstOrNull { it.id == card.frameStyleId },
                { previewId = null }, { previewId = null; openEditor(card.id) }, { actions.share(card) },
                { previewId = null; actions.cardAction(card, CardMenuAction.QUICK) }, { operation ->
                    if (operation == CardMenuAction.DELETE) { previewId = null; removeCard = card }
                    else actions.cardAction(card, operation)
                }, { previewId = null; moveCard = card }, enabled,
                readOnly = cards.isReadOnly(card.id), motion = rememberCardMotionPreview(stickers, card))
        }
        removeCard?.let { card -> CardDeleteDialog("删除这张拍立得？", card.title, { removeCard = null }, {
            removeCard = null; actions.cardAction(card, CardMenuAction.DELETE)
        }) }
        moveCard?.let { card ->
            CardAlbumTargetDialog(albums, albumContaining(albums, card.id)?.id, { moveCard = null }) { target ->
                moveCard = null; actions.work({ cards.moveCards(listOf(card.id), target) }) { selectPhoto(card); selectedAlbum = target }
            }
        }
        if (createAlbum) CardNameDialog("新建相册", "", { createAlbum = false }, { name ->
            createAlbum = false; actions.work({ cards.createAlbum(name) }) { selectAlbum(it.id) }
        })
        renameAlbum?.let { id -> CardNameDialog("重命名相册", albums.firstOrNull { it.id == id }?.name.orEmpty(), { renameAlbum = null }, { name ->
            renameAlbum = null; actions.work({ cards.renameAlbum(id, name) })
        }) }
        removeAlbum?.let { id -> CardDeleteDialog("删除相册？", "照片将移到默认相册，仍可继续查看。", { removeAlbum = null }, {
            removeAlbum = null; actions.work({ cards.removeAlbum(id) }) { selectAlbum(CardRepository.DEFAULT_ALBUM) }
        }) }
        CardQuickExportDialog(actions)
    }
}
