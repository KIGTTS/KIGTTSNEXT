@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

internal enum class CardEditorMode { CROP, STICKERS }

@Composable
internal fun CardEditorTabs(mode: CardEditorMode, select: (CardEditorMode) -> Unit) {
    PrimaryTabRow(mode.ordinal, Modifier.testTag("card-editor-tabs"), containerColor = Color.Transparent, divider = {}) {
        CardEditorMode.entries.forEach { option -> Tab(option == mode, feedbackClick { select(option) },
            modifier = Modifier.testTag("card-editor-tab/" + option.name),
            text = { Text(if (option == CardEditorMode.CROP) "裁剪" else "贴纸") },
            icon = { Icon(if (option == CardEditorMode.CROP) Icons.Default.Crop else Icons.Default.Collections, null, Modifier.size(24.dp)) }) }
    }
}
