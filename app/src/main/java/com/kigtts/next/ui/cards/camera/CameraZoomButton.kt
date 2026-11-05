@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

/** Equal diameter and zero button text padding keep the ratio in one line without shrinking its touch target. */
@Composable
internal fun CameraZoomButton(label: String, description: String, enabled: Boolean, click: () -> Unit,
    modifier: Modifier = Modifier, textTag: String? = null, selected: Boolean = true) {
    val press = rememberImmediateButtonPress(remember { MutableInteractionSource() })
    LaunchedEffect(enabled) { if (!enabled) press.cancel() }
    FilledTonalIconButton(onClick = feedbackClick { press.click(); click() },
        modifier = modifier.size(48.dp).then(if (enabled) Modifier.immediateButtonPress(press) else Modifier)
            .semantics { contentDescription = description },
        enabled = enabled, interactionSource = press.native,
        shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(12.dp)),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface).keepColorsWhenLocked()) {
        Text(label, if (textTag != null) Modifier.testTag(textTag) else Modifier,
            maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
    }
}
