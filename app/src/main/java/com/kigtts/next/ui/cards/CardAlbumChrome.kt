@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.LocalPageActive

/** The same pickup/anchor feedback as the home sheet, with a dedicated gesture owner. */
@Composable
internal fun CardAlbumChrome(state: AnchoredDraggableState<Int>, landscape: Boolean, enabled: Boolean,
    expanded: Boolean, reveal: State<Float>, canRename: Boolean, canDelete: Boolean,
    toggle: () -> Unit, create: () -> Unit, manage: () -> Unit, rename: () -> Unit, remove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val colors = compactTonalIconColors().let { if (!LocalPageActive.current) it.keepColorsWhenLocked() else it }
    val drag = rememberCardAlbumDrag(state, landscape, enabled)
    val toggleButton: @Composable () -> Unit = {
        ToolButton(if (expanded) "收起相册" else "展开相册",
            if (landscape) { if (expanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft }
            else { if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp }, toggle,
            Modifier.width(32.dp).testTag("toggle-card-albums"), size = 32.dp,
            shapes = IconButtonDefaults.shapes(RoundedCornerShape(16.dp), RoundedCornerShape(8.dp)), colors = colors, enabled = enabled)
    }
    val actionModifier = Modifier.width(32.dp).graphicsLayer {
        alpha = if (landscape) reveal.value.coerceAtMost(.35f) / .35f else 1f
    }
    val actionsEnabled = enabled && (!landscape || expanded)
    val createButton: @Composable () -> Unit = {
        ToolButton("新建相册", Icons.Default.CreateNewFolder, create, actionModifier.testTag("create-card-album"),
            size = 32.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(16.dp), RoundedCornerShape(8.dp)),
            colors = colors, enabled = actionsEnabled)
    }
    val moreButton: @Composable () -> Unit = {
        Box {
            ToolButton("相册操作", Icons.Default.MoreVert, { menu = true }, actionModifier.testTag("card-album-menu"),
                size = 32.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(16.dp), RoundedCornerShape(8.dp)),
                colors = colors, enabled = actionsEnabled && canRename)
            DropdownMenu(menu && enabled, { menu = false }) {
                DropdownMenuItem({ Text("整理相册") }, feedbackClick { menu = false; manage() }, leadingIcon = { Icon(Icons.Default.Sort, null) })
                DropdownMenuItem({ Text("重命名") }, feedbackClick { menu = false; rename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                if (canDelete) DropdownMenuItem({ Text("删除相册") }, feedbackClick { menu = false; remove() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
            }
        }
    }
    val grip: @Composable (Modifier) -> Unit = { modifier ->
        val length by animateDpAsState(if (drag.active()) 88.dp else 76.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "album-grip-length")
        val thickness by animateDpAsState(if (drag.active()) 7.dp else 5.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "album-grip-thickness")
        Box(modifier.then(drag.modifier).testTag("card-album-handle")
            .semantics { contentDescription = "调整相册面板" }.clickable(enabled = enabled, onClick = toggle), contentAlignment = Alignment.Center) {
            Box(Modifier.then(if (landscape) Modifier.width(thickness).height(length) else Modifier.width(length).height(thickness))
                .testTag("card-album-grip").clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        }
    }
    if (landscape) Column(Modifier.width(42.dp).fillMaxHeight().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().height(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            toggleButton(); Spacer(Modifier.height(8.dp)); createButton()
        }
        grip(Modifier.weight(1f).fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.BottomCenter) { moreButton() }
    } else Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) { moreButton() }
        grip(Modifier.weight(1f).fillMaxHeight())
        Row(Modifier.width(72.dp), verticalAlignment = Alignment.CenterVertically) { createButton(); Spacer(Modifier.width(8.dp)); toggleButton() }
    }
}
