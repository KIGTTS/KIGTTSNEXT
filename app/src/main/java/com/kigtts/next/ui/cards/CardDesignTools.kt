@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.cards.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

@Composable
internal fun CardDesignTools(cards: CardRepository, draft: PolaroidCard, enabled: Boolean,
    change: (PolaroidCard) -> Unit, message: (String) -> Unit) {
    val state by cards.designs.state.collectAsState()
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun work(action: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message(error.message ?: "未能保存预设") }
            finally { busy = false }
        }
    }
    CardEditorSection("相框预设", Icons.Default.Bookmarks, "card-frame-presets") {
        PillButton("保存当前设计为默认预设", Icons.Default.BookmarkAdd, { naming = true }, enabled = enabled && !busy)
        Text("保留边框、贴纸及文字样式，自动用于之后新建的照片。拍摄字段会换成新照片的信息。", style = MaterialTheme.typography.bodySmall)
        state.presets.forEach { preset ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.defaultId == preset.id, { work { cards.designs.defaultPreset(preset.id) } }, enabled = enabled && !busy)
                Text(preset.name, Modifier.weight(1f))
                ToolButton("应用预设", Icons.Default.Check, {
                    scope.launch {
                        try { val card = withContext(Dispatchers.IO) { cards.designs.apply(draft, preset.id) }; change(card) }
                        catch (error: Exception) { message(error.message ?: "无法应用预设") }
                    }
                }, enabled = enabled && !busy)
                ToolButton("删除预设", Icons.Default.DeleteOutline, { work { cards.designs.removePreset(preset.id) } }, enabled = enabled && !busy)
            }
        }
        if (state.defaultId != null) TextButton({ work { cards.designs.defaultPreset(null) } }, enabled = enabled && !busy) { Text("不自动应用预设") }
    }
    if (naming) CardNameDialog("保存默认相框预设", "", { naming = false }, { name ->
        naming = false; work { cards.designs.savePreset(name, draft, makeDefault = true) }
    }, maximum = 64, inputLabel = "预设名称")
}

@Composable
internal fun CardPhotoFieldTools(cards: CardRepository, card: PolaroidCard, enabled: Boolean,
    put: (CardLayer) -> Unit, remove: (String) -> Unit, message: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf<PhotoTextField?>(null) }
    fun insert(field: PhotoTextField, text: String) {
        val previous = cards.designs.fieldLayerId(card.id, field)?.let { id -> card.layers.firstOrNull { it.id == id } }
        val layer = previous?.copy(kind = CardLayerKind.PHOTO_INFO, text = text) ?: CardLayer(kind = CardLayerKind.PHOTO_INFO, text = text)
        scope.launch {
            try { withContext(Dispatchers.IO) { cards.designs.bind(card.id, layer, field) }; put(layer) }
            catch (error: Exception) { message(error.message ?: "无法添加拍摄字段") }
        }
    }
    CardEditorSection("拍摄信息", Icons.Default.Event, "card-capture-fields") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoTextField.entries.forEach { field ->
                FilterChip(false, {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                val existing = withContext(Dispatchers.IO) {
                                    if (!cards.designs.hasFacts(card.id)) cards.designs.freeze(card.id, PolaroidPhotoFacts.read(cards.stickers.imageFile(card.originalImageId ?: card.imageId.orEmpty()), false))
                                    cards.designs.facts(card.id)
                                }
                                val facts = PolaroidPhotoFacts.enrich(context, existing, setOf(field))
                                withContext(Dispatchers.IO) { cards.designs.freeze(card.id, facts) }
                                val missing = when (field) {
                                    PhotoTextField.LOCATION -> facts.location == null
                                    PhotoTextField.WEATHER -> facts.weather == null
                                    else -> facts.capturedAt == null
                                }
                                if (missing) manual = field else insert(field, facts.text(field))
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) { message(error.message ?: "未能取得拍摄信息") }
                            finally { busy = false }
                        }
                    }
                }, { Text(field.label) }, enabled = enabled && !busy)
            }
        }
        Text("拍摄时固定保存；缺少信息时可手动填写。天气为 Open-Meteo 模型估计。", style = MaterialTheme.typography.bodySmall)
        card.layers.filter { it.kind == CardLayerKind.PHOTO_INFO }.forEach { layer ->
            OutlinedTextField(layer.text, { if (it.length <= 128) put(layer.copy(text = it)) }, Modifier.fillMaxWidth(),
                label = { Text("附加信息") }, singleLine = true, enabled = enabled,
                trailingIcon = { ToolButton("移除这条附加信息", Icons.Default.Close, { remove(layer.id) }, enabled = enabled) })
        }
    }
    manual?.let { field -> CardNameDialog("填写${field.label}", "", { manual = null }, { text -> manual = null; insert(field, text) }, maximum = 128,
        inputLabel = "照片未保存此信息，请填写拍摄时的${field.label}") }
}
