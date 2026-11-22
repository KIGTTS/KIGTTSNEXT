@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

internal enum class CardEditorMode { CROP, FILTER, FRAME, STICKERS, TEXT }

@Composable
internal fun CardEditorTabs(mode: CardEditorMode, select: (CardEditorMode) -> Unit, compact: Boolean = false, personal: Boolean = false) {
    val options = if (personal) listOf(CardEditorMode.CROP, CardEditorMode.STICKERS) else CardEditorMode.entries
    PrimaryTabRow(options.indexOf(mode).coerceAtLeast(0), Modifier.testTag("card-editor-tabs"), containerColor = Color.Transparent, divider = {}) {
        options.forEach { option -> Tab(option == mode, feedbackClick { select(option) },
            modifier = Modifier.testTag("card-editor-tab/" + option.name),
            text = { Text(when (option) { CardEditorMode.CROP -> if (personal) uiText("camera.watermark_background") else uiText("photo.crop"); CardEditorMode.FILTER -> uiText("camera.filters"); CardEditorMode.FRAME -> uiText("photo.watermark"); CardEditorMode.STICKERS -> uiText("photo.sticker"); CardEditorMode.TEXT -> uiText("photo.text") }) },
            icon = if (compact) null else ({ Icon(when (option) { CardEditorMode.FILTER -> Icons.Default.Tune; CardEditorMode.CROP -> Icons.Default.Crop; CardEditorMode.FRAME -> Icons.Default.CropOriginal
                CardEditorMode.STICKERS -> Icons.Default.Collections; CardEditorMode.TEXT -> Icons.Default.TextFields }, null, Modifier.size(22.dp)) })) }
    }
}
