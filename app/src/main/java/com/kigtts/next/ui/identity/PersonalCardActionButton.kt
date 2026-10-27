@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

/** Matches the subtitle corner controls; the native container also owns the ripple boundary. */
@Composable internal fun PersonalCardActionButton(label: String, tag: String, interactive: Boolean,
    click: () -> Unit, modifier: Modifier = Modifier, index: Int = 0, count: Int = 1,
    content: @Composable () -> Unit) {
    val shape = connectedShape(CornerNeighbors(start = index > 0, end = index < count - 1), outer = 16.dp)
    val bounds = modifier.size(32.dp).testTag(tag)
    val color = Color.Black.copy(alpha = .36f)
    if (!interactive) {
        Box(bounds.clip(shape).background(color), contentAlignment = Alignment.Center) { content() }
        return
    }
    val press = rememberImmediateButtonPress(remember { MutableInteractionSource() })
    IconActionHint(label) {
        FilledTonalIconButton(onClick = feedbackClick { press.click(); click() },
            modifier = bounds.iconLongAction(true, null).immediateButtonPress(press)
                .semantics { contentDescription = label },
            interactionSource = press.native,
            shapes = IconButtonDefaults.shapes(shape, RoundedCornerShape(8.dp)),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = color, contentColor = Color.White),
            content = content)
    }
}
