package com.kigtts.next.ui.cards.camera

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
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
    back: () -> Unit, save: () -> Unit, error: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember(draft.id) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val selected = index.coerceIn(draft.layers.indices)
    val layer = draft.layers[selected]
    val latest = rememberUpdatedState(draft)
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !loading) {
            loading = true
            scope.launch {
                try {
                    val added = withContext(Dispatchers.IO) {
                        val svg = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                            AppearanceAssets.readBounded(it, ExpressionPack.MAX_SVG).toString(Charsets.UTF_8)
                        }
                        ExpressionLayer(svg).also {
                            CompiledExpression.compile(ExpressionEffect("preview", "SVG", listOf(it)))
                        }
                    }
                    val current = latest.value
                    if (current.layers.size < 8) {
                        index = current.layers.size
                        change(current.copy(layers = current.layers + added))
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { error(context.uiCopy("expression.file_invalid")) }
                finally { loading = false }
            }
        }
    }
    fun edit(value: ExpressionLayer) {
        change(draft.copy(layers = draft.layers.toMutableList().apply { set(selected, value) }))
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)
        .testTag("camera-expression-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, uiText("action.back")) }
            Text(uiText("expression.edit"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Button(save, enabled = draft.name.isNotBlank() && !saving && !loading) { Text(uiText("action.save")) }
        }
        ExpressionArtPreview(draft, layer.anchor)
        OutlinedTextField(draft.name, { change(draft.copy(name = it.take(80))) }, Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(uiText("expression.name")) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(draft.layers.indices.toList()) { item ->
                    FilterChip(selected == item, { index = item }, {
                        Text(uiText("expression.layer", "number" to (item + 1).toString()))
                    })
                }
            }
            IconButton({ add.launch(arrayOf("image/svg+xml", "application/octet-stream")) }, enabled = !loading && draft.layers.size < 8) {
                Icon(Icons.Default.Add, uiText("expression.add_svg"))
            }
            IconButton({
                change(draft.copy(layers = draft.layers.filterIndexed { item, _ -> item != selected }))
                index = selected.coerceAtMost(draft.layers.lastIndex - 1)
            }, enabled = draft.layers.size > 1) { Icon(Icons.Default.DeleteOutline, uiText("expression.remove_layer")) }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("expression.blur_base"), Modifier.weight(1f))
            Switch(layer.blurBase, { edit(layer.copy(blurBase = it)) })
        }
        ExpressionSlider("scale", layer.scale, .15f..3f) { edit(layer.copy(scale = it)) }
        ExpressionSlider("x", layer.x, -1f..1f) { edit(layer.copy(x = it)) }
        ExpressionSlider("y", layer.y, -1f..1f) { edit(layer.copy(y = it)) }
        ExpressionSlider("rotation", layer.rotation, -180f..180f) { edit(layer.copy(rotation = it)) }
        ExpressionSlider("opacity", layer.opacity, .1f..1f) { edit(layer.copy(opacity = it)) }
    }
}

@Composable
private fun ExpressionSlider(name: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Column {
        Text(uiText("expression.adjust." + name), style = MaterialTheme.typography.labelLarge)
        Slider(value, change, valueRange = range)
    }
}
