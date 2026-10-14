@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ElasticTab
import com.kigtts.next.ui.components.CornerNeighbors
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*

internal enum class CardEditorMode { CROP, FRAME, STICKERS, TEXT }

@Composable
internal fun CardEditorTabs(mode: CardEditorMode, select: (CardEditorMode) -> Unit, compact: Boolean = false) {
    val sources = remember { CardEditorMode.entries.map { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp).selectableGroup().testTag("card-editor-tabs"),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        CardEditorMode.entries.forEachIndexed { index, option ->
            ElasticTab(option == mode, { select(option) }, sources[index],
                CornerNeighbors(start = index > 0, end = index < CardEditorMode.entries.lastIndex),
                pressed.getOrElse(index - 1) { false } || pressed.getOrElse(index + 1) { false },
                modifier = Modifier.weight(1f).testTag("card-editor-tab/" + option.name).semantics { role = Role.Tab },
                label = { Text(when (option) { CardEditorMode.CROP -> "裁剪"; CardEditorMode.FRAME -> "边框"; CardEditorMode.STICKERS -> "贴纸"; CardEditorMode.TEXT -> "文字" }, maxLines = 1) },
                icon = { if (!compact) Icon(when (option) { CardEditorMode.CROP -> Icons.Default.Crop; CardEditorMode.FRAME -> Icons.Default.CropOriginal
                    CardEditorMode.STICKERS -> Icons.Default.Collections; CardEditorMode.TEXT -> Icons.Default.TextFields }, null, Modifier.size(16.dp)) })
        }
    }
}
