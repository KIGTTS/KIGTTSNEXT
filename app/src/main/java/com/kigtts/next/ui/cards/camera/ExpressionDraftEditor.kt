@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.ui.cards.StickerPickerSheet
import com.kigtts.next.ui.components.PillButton
import com.kigtts.next.ui.media.PickGalleryImage
import com.kigtts.next.ui.media.VisualMediaPicker
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.AppearanceAssets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun ExpressionDraftEditor(draft: ExpressionEffect, saving: Boolean, change: (ExpressionEffect) -> Unit,
    stickers: StickerRepository, save: () -> Unit, error: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by rememberSaveable(draft.id) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var addLayer by remember { mutableStateOf(false) }
    var replaceSvg by rememberSaveable(draft.id) { mutableStateOf<Int?>(null) }
    var replacePhoto by rememberSaveable(draft.id) { mutableStateOf<Int?>(null) }
    var pickSticker by remember { mutableStateOf<Int?>(null) }
    val selected = index.coerceIn(draft.layers.indices)
    val layer = draft.layers[selected]
    val latest = rememberUpdatedState(draft)
    fun loadArtwork(replace: Int?, failure: String = "expression.file_invalid", read: () -> ExpressionLayer) {
        if (!loading && !saving) {
            loading = true
            scope.launch {
                try {
                    val artwork = withContext(Dispatchers.IO) {
                        read().also {
                            CompiledExpression.compile(ExpressionEffect("preview", "SVG", listOf(it)))
                        }
                    }
                    val current = latest.value
                    if (replace != null && replace in current.layers.indices) {
                        val layers = current.layers.toMutableList()
                        layers[replace] = layers[replace].copy(svg = artwork.svg, bitmapWebp = artwork.bitmapWebp)
                        change(current.copy(layers = layers))
                    } else if (replace == null && current.layers.size < 8) {
                        index = current.layers.size
                        change(current.copy(layers = current.layers + artwork))
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { error(context.uiCopy(failure)) }
                finally { loading = false }
            }
        }
    }
    val selectSvg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val replace = replaceSvg
        replaceSvg = null
        if (uri != null) loadArtwork(replace) {
            val svg = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                AppearanceAssets.readBounded(it, ExpressionPack.MAX_SVG).toString(Charsets.UTF_8)
            }
            ExpressionLayer(svg)
        }
    }
    val selectPhoto = rememberLauncherForActivityResult(VisualMediaPicker()) { uri ->
        val replace = replacePhoto
        replacePhoto = null
        if (uri != null) loadArtwork(replace, "photo.sticker_import_failed") { expressionPhotoLayer(context, uri) }
    }
    fun openSvg(target: Int?) {
        replaceSvg = target
        selectSvg.launch(arrayOf("image/svg+xml", "application/octet-stream"))
    }
    fun openPhoto(target: Int?) {
        replacePhoto = target
        selectPhoto.launch(PickGalleryImage)
    }
    pickSticker?.let { replace ->
        StickerPickerSheet(stickers, { pickSticker = null }) { item ->
            pickSticker = null
            loadArtwork(replace) {
                ExpressionLayer(EMPTY_EXPRESSION_SVG, bitmapWebp = expressionStickerImage(requireNotNull(stickers.imageFile(item.imageId))))
            }
        }
    }
    fun updateLayer(target: Int, value: ExpressionLayer) {
        val current = latest.value
        if (target in current.layers.indices) change(current.copy(layers = current.layers.toMutableList().apply { set(target, value) }))
    }
    fun edit(value: ExpressionLayer) = updateLayer(selected, value)
    fun removeLayer(target: Int) {
        val current = latest.value
        if (current.layers.size <= 1 || target !in current.layers.indices) return
        change(current.copy(layers = current.layers.filterIndexed { item, _ -> item != target }))
        index = target.coerceAtMost(current.layers.lastIndex - 1)
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)
        .testTag("camera-expression-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpressionArtPreview(draft, selected, !loading && !saving, { index = it }, ::updateLayer, ::removeLayer)
        OutlinedTextField(draft.name, { change(draft.copy(name = it.take(80))) }, Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(uiText("expression.name")) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("expression.blur_base"), Modifier.weight(1f))
            Switch(draft.blurFaceEnabled, { change(latest.value.copy(blurFace = it)) }, enabled = !loading && !saving)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(draft.layers.indices.toList()) { item ->
                    FilterChip(selected == item, { index = item }, {
                        Text(uiText("expression.layer", "number" to (item + 1).toString()))
                    })
                }
            }
            Box {
                IconButton({ addLayer = true }, enabled = !loading && !saving && draft.layers.size < 8) {
                    Icon(Icons.Default.Add, uiText("expression.add_layer"))
                }
                DropdownMenu(addLayer, { addLayer = false }) {
                    DropdownMenuItem({ Text(uiText("expression.add_photo")) }, {
                        addLayer = false
                        openPhoto(null)
                    }, leadingIcon = { Icon(Icons.Default.Collections, null) })
                    DropdownMenuItem({ Text(uiText("expression.add_svg")) }, {
                        addLayer = false
                        openSvg(null)
                    }, leadingIcon = { Icon(Icons.Default.Image, null) })
                }
            }
            IconButton({ removeLayer(selected) }, enabled = !loading && !saving && draft.layers.size > 1) {
                Icon(Icons.Default.DeleteOutline, uiText("expression.remove_layer"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PillButton(uiText("expression.replace_photo"), Icons.Default.Image, { openPhoto(selected) },
                Modifier.testTag("expression-replace-photo"), enabled = !loading && !saving)
            PillButton(uiText("expression.replace_svg"), Icons.Default.Image, { openSvg(selected) },
                Modifier.testTag("expression-replace-svg"), enabled = !loading && !saving)
            PillButton(uiText("photo.choose_sticker"), Icons.Default.Collections, { pickSticker = selected },
                Modifier.testTag("expression-layer-from-library"), enabled = !loading && !saving)
        }
        Text(uiText("expression.anchor"), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ExpressionAnchor.entries) { anchor ->
                FilterChip(layer.anchor == anchor, { edit(layer.copy(anchor = anchor)) }, {
                    Text(uiText("expression.anchor." + anchor.name.lowercase(Locale.ROOT)))
                })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("expression.fringe"), Modifier.weight(1f))
            Switch(layer.hideUnderFringe, { edit(layer.copy(hideUnderFringe = it)) })
        }
        ExpressionSlider("scale", layer.scale, .15f..3f) { edit(layer.copy(scale = it)) }
        ExpressionSlider("x", layer.x, -1f..1f) { edit(layer.copy(x = it)) }
        ExpressionSlider("y", layer.y, -1f..1f) { edit(layer.copy(y = it)) }
        ExpressionSlider("rotation", layer.rotation, -180f..180f) { edit(layer.copy(rotation = it)) }
        ExpressionSlider("opacity", layer.opacity, .1f..1f) { edit(layer.copy(opacity = it)) }
        Button(save, Modifier.align(Alignment.End), enabled = draft.name.isNotBlank() && !saving && !loading) {
            Text(uiText("action.save"))
        }
    }
}

@Composable
private fun ExpressionSlider(name: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Column {
        Text(uiText("expression.adjust." + name), style = MaterialTheme.typography.labelLarge)
        Slider(value, change, valueRange = range)
    }
}
