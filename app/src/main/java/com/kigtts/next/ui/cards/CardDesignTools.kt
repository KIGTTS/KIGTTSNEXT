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
        Text("将边框、贴纸和文字样式用于新照片，日期、地点等信息随拍摄更新。", style = MaterialTheme.typography.bodySmall)
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
