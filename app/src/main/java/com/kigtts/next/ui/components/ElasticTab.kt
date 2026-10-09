@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Round exposed ends and elastic corners toward neighbouring tabs. */
@Composable
fun ElasticTab(selected: Boolean, click: () -> Unit, source: MutableInteractionSource,
    neighbors: CornerNeighbors, neighborPressed: Boolean, modifier: Modifier = Modifier,
    icon: @Composable () -> Unit, label: @Composable () -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null, dragging: Boolean = false) {
    val pressed by source.collectIsPressedAsState()
    val shape = connectedShape(neighbors, pressed, neighborPressed, outer = 16.dp)
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tab-drag-elevation")
    val restingBorder = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
    val borderColor by animateColorAsState(if (dragging) Color.Transparent else (restingBorder.brush as SolidColor).value,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "tab-drag-border")
    FilterChip(selected, feedbackClick(click), label, modifier.height(32.dp)
        .semantics { if (dragging) stateDescription = "正在拖动分组" },
        interactionSource = source, leadingIcon = icon, trailingIcon = trailingIcon,
        colors = compactTabColors(dragging),
        border = BorderStroke(restingBorder.width, borderColor),
        elevation = FilterChipDefaults.filterChipElevation(elevation = elevation, pressedElevation = elevation,
            focusedElevation = elevation, hoveredElevation = elevation, draggedElevation = elevation),
        shape = shape)
}
