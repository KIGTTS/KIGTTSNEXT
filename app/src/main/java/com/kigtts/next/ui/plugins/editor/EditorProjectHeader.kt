package com.kigtts.next.ui.plugins.editor

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.identity.prepareAvatarSource
import com.kigtts.next.media.decodeResourceVector
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.PLUGIN_EDITOR_ID
import com.kigtts.next.theme.cropBackground
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.identity.SquareImageCropDialog
import com.kigtts.next.ui.management.ResourceAvatar
import com.kigtts.next.ui.settings.*
import com.kigtts.plugin.api.ManagementItem
import com.kigtts.plugin.api.PluginResource
import java.io.File
import kotlinx.coroutines.*

@Composable
internal fun EditorProjectHeader(model: AppViewModel, owner: String, item: ManagementItem, busy: Boolean,
    resolve: (PluginResource) -> File?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val project = item.id
    var iconDialog by rememberSaveable(project) { mutableStateOf(false) }
    var symbolPicker by rememberSaveable(project) { mutableStateOf(false) }
    var profileDialog by rememberSaveable(project) { mutableStateOf(false) }
    var crop by remember(project) { mutableStateOf<File?>(null) }
    val failure = uiText("editor.icon_failed")
    val nameAction = if (owner == PLUGIN_EDITOR_ID) "rename/$project" else "project-name/$project"
    val saveSymbol = if (owner == PLUGIN_EDITOR_ID) "save-icon/$project" else "project-save-icon/$project"
    suspend fun submitArtwork(file: File, extension: String) {
        try {
            val path = model.pluginManager.importResource(owner, "imports/editor-icons", "icon.$extension", false) { file.inputStream() }
            model.invoke(owner, "editor-icon-file/$project", mapOf("resourcePath" to path))
        } finally { file.delete() }
    }
    val rasterPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { crop = withContext(Dispatchers.IO) { prepareAvatarSource(context, uri) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.message(failure) }
        }
    }
    val vectorPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val svg = withContext(Dispatchers.IO) {
                    val file = File.createTempFile("editor-icon-", ".svg", context.cacheDir)
                    try {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                total += count
                                require(total <= 1_048_576)
                                output.write(buffer, 0, count)
                            }
                        } }
                        require(decodeResourceVector(file) != null)
                        file
                    } catch (error: Exception) { file.delete(); throw error }
                }
                submitArtwork(svg, "svg")
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.message(failure) }
        }
    }
    DisposableEffect(crop) {
        val current = crop
        onDispose { current?.delete() }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("editor-project-header"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.clickable(enabled = !busy, role = Role.Button, onClickLabel = uiText("editor.edit_icon"),
            onClick = feedbackClick { iconDialog = true }).testTag("editor-edit-icon")) {
            ResourceAvatar(item, resolve, 80.dp)
            Surface(Modifier.align(Alignment.BottomEnd).size(24.dp), shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(15.dp)) }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = feedbackClick { model.invoke(owner, nameAction) }, enabled = !busy,
                    modifier = Modifier.testTag("editor-edit-name")) {
                    Icon(Icons.Default.Edit, uiText("editor.edit_name"), Modifier.size(19.dp))
                }
            }
            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SettingsGroup(uiText("editor.package")) {
        SettingItem(uiText("editor.package_details"), project, MaterialSymbols.vector("inventory_2"), SettingPosition.SINGLE,
            enabled = !busy, onClick = { profileDialog = true }, modifier = Modifier.testTag("editor-package-details"))
    }
    if (profileDialog) EditorPackageDialog(model, owner, project) { profileDialog = false }
    if (iconDialog) MotionAlertDialog(onDismissRequest = { iconDialog = false },
        title = { Text(uiText("editor.edit_icon")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val choices = listOf(
                    Triple("editor.md_icon", "widgets", { iconDialog = false; symbolPicker = true }),
                    Triple("editor.raster_icon", "crop", { iconDialog = false; rasterPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }),
                    Triple("editor.svg_icon", "image", { iconDialog = false; vectorPicker.launch(arrayOf("image/svg+xml", "application/xml", "text/xml", "application/octet-stream")) })
                )
                choices.forEachIndexed { index, (label, symbol, select) ->
                    SettingItem(uiText(label), "", MaterialSymbols.vector(symbol),
                        when (index) { 0 -> SettingPosition.FIRST; 2 -> SettingPosition.LAST; else -> SettingPosition.MIDDLE },
                        onClick = select)
                }
            }
        }, confirmButton = {
            MotionTextButton(onClick = dialogFeedbackClick { iconDialog = false }) { Text(uiText("action.cancel")) }
        })
    if (symbolPicker) MaterialSymbolPicker({ name ->
        model.invoke(owner, saveSymbol, mapOf("value" to name)); symbolPicker = false
    }, { symbolPicker = false }, item.symbol)
    crop?.let { source ->
        SquareImageCropDialog(source, close = { crop = null }, save = { image ->
            crop = null
            scope.launch { try { submitArtwork(image, "png") }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { model.message(failure) } }
        }, roundPreview = false, title = uiText("editor.crop_icon")) { image, selection ->
            val square = cropBackground(image, selection)
            val scaled = if (square.width > 384) Bitmap.createScaledBitmap(square, 384, 384, true) else square
            val file = File.createTempFile("editor-icon-crop-", ".png", context.cacheDir)
            try { file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }; file }
            catch (error: Exception) { file.delete(); throw error }
            finally { if (scaled !== square) scaled.recycle(); if (square !== image) square.recycle() }
        }
    }
}
