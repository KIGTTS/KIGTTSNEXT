@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

@Composable
fun PanelDragIndicator(active: Boolean, tag: String, modifier: Modifier = Modifier, baseWidth: Dp = 76.dp, vertical: Boolean = false) {
    val width by animateDpAsState(if (active) baseWidth * 1.16f else baseWidth, MaterialTheme.motionScheme.fastSpatialSpec(), label = "handle-width")
    val height by animateDpAsState(if (active) 7.dp else 4.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "handle-thickness")
    Box(modifier.then(if (vertical) Modifier.width(height).height(width) else Modifier.width(width).height(height)).testTag(tag)
        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge))
}
