@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.home.PanelDragIndicator

@Composable internal fun CardEditorPanelHeader(wide: Boolean, open: Boolean, enabled: Boolean,
    drag: CardPanelDrag, toggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.then(drag.modifier).testTag("card-editor-panel-header"), contentAlignment = Alignment.Center) {
        PanelDragIndicator(drag.active(), "card-editor-panel-handle", vertical = wide)
        ToolButton(if (open) uiText("photo.edit_panel_close") else uiText("photo.edit_panel_open"),
            if (wide) { if (open) Icons.Default.ChevronRight else Icons.Default.ChevronLeft }
            else { if (open) Icons.Default.ExpandMore else Icons.Default.ExpandLess }, toggle,
            Modifier.align(if (wide) Alignment.TopCenter else Alignment.CenterEnd)
                .padding(if (wide) PaddingValues(top = 8.dp) else PaddingValues(end = 12.dp))
                .width(32.dp).testTag("toggle-card-editor-panel"),
            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(8.dp)), size = 32.dp, enabled = enabled)
    }
}
