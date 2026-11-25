@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.ExpressionEffect
import com.kigtts.next.i18n.uiText
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun CameraFaceStickerPicker(list: List<ExpressionEffect>, options: CameraOptions, vertical: Boolean,
    enabled: Boolean, close: () -> Unit, manage: () -> Unit, store: (() -> Unit)?) {
    val chrome: @Composable () -> Unit = {
        ToolButton(uiText("action.close"), if (vertical) Icons.Default.ChevronRight else Icons.Default.ExpandMore, close,
            Modifier.size(48.dp), size = 40.dp, enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent))
        ToolButton(uiText("expression.edit"), Icons.Default.Edit, manage, Modifier.size(48.dp), size = 40.dp,
            enabled = enabled, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent))
        if (store != null) ToolButton(uiText("expression.store"), Icons.Default.Storefront, store,
            Modifier.size(48.dp), size = 40.dp, enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent))
    }
    val tiles: LazyListScope.() -> Unit = {
        item("off") {
            CameraStickerTile(uiText("option.off"), null, options.expressionId == null, enabled) { options.chooseExpression(null) }
        }
        items(list, key = { it.id }) { effect ->
            CameraStickerTile(effect.name, effect, options.expressionId == effect.id, enabled) { options.chooseExpression(effect.id) }
        }
    }
    if (vertical) Row(Modifier.fillMaxHeight().padding(8.dp).testTag("camera-sticker-picker")) {
        Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) { chrome() }
        LazyColumn(Modifier.width(80.dp).fillMaxHeight(), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp), content = tiles)
    } else Column(Modifier.fillMaxWidth().padding(8.dp).testTag("camera-sticker-picker")) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("expression.title"), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            chrome()
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = tiles)
    }
}

@Composable private fun CameraStickerTile(label: String, effect: ExpressionEffect?, active: Boolean, enabled: Boolean,
    click: () -> Unit) {
    Surface(onClick = feedbackClick(click), enabled = enabled, modifier = Modifier.size(80.dp).semantics { selected = active },
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (active) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
        Box(Modifier.fillMaxSize()) {
            if (effect == null) Icon(Icons.Default.Block, null, Modifier.align(Alignment.Center).padding(bottom = 8.dp))
            else expressionThumbnail(effect)?.let { Image(it, null, Modifier.fillMaxSize()) }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
            Text(label, Modifier.align(Alignment.BottomStart).padding(7.dp), color = Color.White,
                style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}
