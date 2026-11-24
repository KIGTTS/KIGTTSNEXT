@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards.camera

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.components.ModalPageTransition
import com.kigtts.next.ui.components.connectedShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun CameraExpressionsSheet(
    library: ExpressionLibrary, options: CameraOptions, store: (() -> Unit)?,
    dismiss: () -> Unit, message: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by library.revision.collectAsState()
    val choices by produceState(emptyList<ExpressionEffect>(), library, revision, androidx.compose.ui.platform.LocalConfiguration.current) {
        value = withContext(Dispatchers.IO) { library.choices() }
    }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<ExpressionEffect?>(null) }
    var operation by remember { mutableStateOf(false) }

    fun run(task: suspend () -> Unit) {
        if (operation) return
        operation = true
        scope.launch {
            try { task() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message(context.uiCopy("expression.file_invalid")) }
            finally { operation = false }
        }
    }

    val importPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val selected = withContext(Dispatchers.IO) {
                val file = File.createTempFile("expression-", ".kigexpression", context.cacheDir)
                try {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        file.writeBytes(AppearanceAssets.readBounded(it, ExpressionPack.MAX_PACK))
                    }
                    library.install(file)
                } finally { file.delete() }
            }
            options.chooseExpression(selected)
            message(context.uiCopy("expression.installed"))
        }
    }
    fun share(effect: ExpressionEffect) = run {
        val intent = withContext(Dispatchers.IO) { library.share(effect) }
        context.startActivity(intent)
    }

    ModalBottomSheet(dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !editing)) {
        BackHandler(editing && !operation) { editing = false }
        ModalPageTransition(editing, { if (it) 1 else 0 }) { editor ->
            val current = draft
            if (editor && current != null) {
                ExpressionDraftEditor(current, operation, { draft = it }, { editing = false },
                    save = { run {
                        withContext(Dispatchers.IO) { library.save(current) }
                        options.chooseExpression(current.id)
                        editing = false
                    } }, error = message)
            } else LazyColumn(Modifier.fillMaxWidth().testTag("camera-expressions"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val active = choices.firstOrNull { it.id == options.expressionId }
                item("title") { Text(uiText("expression.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp)) }
                if (active != null) item("preview") { ExpressionArtPreview(active) }
                item("actions") { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton({ importPack.launch(arrayOf("*/*")) }, enabled = !operation) {
                        Icon(Icons.Default.FileOpen, uiText("expression.import"))
                    }
                    FilledTonalIconButton({ draft = library.newDraft(); editing = true }, enabled = !operation) {
                        Icon(Icons.Default.Add, uiText("expression.new"))
                    }
                    if (store != null) FilledTonalButton({ dismiss(); store() }, enabled = !operation) {
                        Icon(Icons.Default.Storefront, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("expression.store"))
                    }
                } }
                item("off") {
                    Surface(onClick = { options.chooseExpression(null) }, enabled = !operation,
                        modifier = Modifier.fillMaxWidth(), shape = connectedShape(CornerNeighbors(below = choices.isNotEmpty())),
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        ListItem(headlineContent = { Text(uiText("option.off")) },
                            leadingContent = { Icon(if (active == null) Icons.Default.Check else Icons.Default.Block, null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
                itemsIndexed(choices, key = { _, effect -> effect.id }) { index, effect ->
                        val selected = effect.id == options.expressionId
                        Surface(onClick = { options.chooseExpression(effect.id) }, enabled = !operation,
                            modifier = Modifier.fillMaxWidth(), shape = connectedShape(CornerNeighbors(above = true, below = index < choices.lastIndex)),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                            ListItem(headlineContent = { Text(effect.name, maxLines = 2) },
                                leadingContent = { Icon(if (selected) Icons.Default.Check else Icons.Default.Face, null) },
                                trailingContent = {
                                    Row {
                                        IconButton({ draft = library.editable(effect); editing = true }, enabled = !operation) {
                                            Icon(Icons.Default.Edit, uiText("expression.edit"))
                                        }
                                        IconButton({ share(effect) }, enabled = !operation) {
                                            Icon(Icons.Default.Share, uiText("expression.share"))
                                        }
                                        if (effect.id.startsWith("local.")) IconButton({ run {
                                            withContext(Dispatchers.IO) { library.remove(effect.id) }
                                            if (selected) options.chooseExpression(null)
                                        } }, enabled = !operation) {
                                            Icon(Icons.Default.DeleteOutline, uiText("expression.remove"))
                                        }
                                    }
                                }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                        }
                }
            }
        }
    }
}

@Composable
internal fun ExpressionArtPreview(effect: ExpressionEffect) {
    val vectors = remember(effect.layers) { effect.layers.map { it.svg } }
    val pictures by produceState<Pair<List<String>, List<android.graphics.Picture>>?>(null, vectors) {
        value = withContext(Dispatchers.Default) {
            runCatching { vectors to vectors.map { com.kigtts.next.media.UiIcons.renderSvgPicture(it, 320) } }.getOrNull()
        }
    }
    val rendered = remember(effect.layers, pictures) {
        pictures?.takeIf { it.first == vectors }?.second?.let { CompiledExpression(effect, it) }
    }
    val ink = MaterialTheme.colorScheme.outlineVariant
    val face = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(Modifier.fillMaxWidth().height(184.dp).clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        val scale = size.minDimension / 320f
        val x = size.width / 2f
        val y = size.height / 2f
        drawOval(face, androidx.compose.ui.geometry.Offset(x - 80f * scale, y - 124f * scale),
            androidx.compose.ui.geometry.Size(160f * scale, 248f * scale))
        val sample = ExpressionFace.SAMPLE
        listOf(sample.leftEye, sample.rightEye, sample.nose, sample.mouthCenter).forEach { point ->
            drawCircle(ink, 4f * scale, androidx.compose.ui.geometry.Offset(x + (point.x - 160f) * scale, y + (point.y - 160f) * scale))
        }
        drawIntoCanvas {
            val native = it.nativeCanvas
            val save = native.save()
            native.translate(x - 160f * scale, y - 160f * scale)
            native.scale(scale, scale)
            rendered?.draw(native, listOf(sample))
            native.restoreToCount(save)
        }
    }
}
