package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.*

/** Gallery management is a child of the editor, never a tab on the card home. */
@Composable
fun StickerLibraryScreen(cards: CardRepository, stickers: StickerRepository, message: (String) -> Unit,
    onQuickImage: (String, String) -> Unit = { _, _ -> }, requestedLibrary: String? = null) {
    val catalog by stickers.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val feedback = rememberUpdatedState(message); val quick = rememberUpdatedState(onQuickImage)
    val actions = remember(cards, stickers) { CardWorkspaceActions(context, cards, stickers, scope, {},
        { feedback.value(it) }, { id, title -> quick.value(id, title) }) }
    var libraryId by rememberSaveable { mutableStateOf(requestedLibrary) }
    var newLibrary by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StickerLibrary?>(null) }
    var deleting by remember { mutableStateOf<StickerLibrary?>(null) }
    var itemMenu by remember { mutableStateOf<StickerItem?>(null) }
    var renaming by remember { mutableStateOf<StickerItem?>(null) }
    var removing by remember { mutableStateOf<StickerItem?>(null) }
    val active = LocalPageActive.current
    val photo = rememberCardPhotoSources({ actions.importPhoto(it, libraryId, asSticker = true) }, message)
    val enabled = active && !actions.busy && !photo.processing
    RegisterPageLoading(actions.busy || photo.processing)
    RegisterPageActions(listOf(ToolbarAction("import-sticker", "导入贴纸", Icons.Default.PhotoLibrary, enabled, click = photo.gallery),
        ToolbarAction("new-sticker-library", "新建贴纸库", Icons.Default.Add, enabled, click = { newLibrary = true })))
    StickerLibraryPanel(catalog.libraries, catalog.items, libraryId, { libraryId = it }, stickers::imageFile, enabled,
        { newLibrary = true }, { editing = it }, { itemMenu = it }, { itemMenu = it }, Modifier.fillMaxSize())
    if (active && newLibrary) CardNameDialog("新建贴纸库", "", { newLibrary = false }, { name ->
        val library = StickerLibrary(name = name); newLibrary = false; libraryId = library.id
        actions.edit(listOf(StickerEdit.PutLibrary(library)))
    })
    if (active) editing?.let { library -> CardNameDialog("编辑贴纸库", library.name, { editing = null }, { name ->
        editing = null; actions.edit(listOf(StickerEdit.PutLibrary(library.copy(name = name))))
    }, remove = if (catalog.libraries.size > 1) ({ editing = null; deleting = library }) else null) }
    if (active) deleting?.let { library -> CardDeleteDialog("删除贴纸库？", "同时移除库中的贴纸，拍立得中的图片会保留。", { deleting = null }, {
        deleting = null; actions.edit(listOf(StickerEdit.RemoveLibraries(listOf(library.id), deleteItems = true)))
    }) }
    if (active) itemMenu?.let { item -> MotionAlertDialog(onDismissRequest = { itemMenu = null }, title = { Text(item.name) }, text = {
        Column {
            MotionTextButton(dialogFeedbackClick { itemMenu = null; actions.quickSticker(item) }, Modifier.fillMaxWidth()) { Text("添加到快捷文本") }
            MotionTextButton(dialogFeedbackClick { itemMenu = null; renaming = item }, Modifier.fillMaxWidth()) { Text("重命名") }
            MotionTextButton(dialogFeedbackClick { itemMenu = null; removing = item }, Modifier.fillMaxWidth()) { Text("删除贴纸") }
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick { itemMenu = null }) { Text("完成") } }) }
    if (active) renaming?.let { item -> CardNameDialog("贴纸名称", item.name, { renaming = null }, { name ->
        renaming = null; actions.edit(listOf(StickerEdit.PutItem(item.copy(name = name))))
    }) }
    if (active) removing?.let { item -> CardDeleteDialog("删除这张贴纸？", "从库中移除“${item.name}”，拍立得中的图片会保留。", { removing = null }, {
        removing = null; actions.edit(listOf(StickerEdit.RemoveItems(listOf(item.id))))
    }) }
}
