@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable
internal fun CardPreviewButton(label: String, icon: ImageVector, click: () -> Unit, modifier: Modifier,
    enabled: Boolean, closing: Boolean, primary: Boolean = false) {
    val press = rememberImmediateButtonPress(remember { MutableInteractionSource() })
    LaunchedEffect(enabled) { if (!enabled) press.cancel() }
    val colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
    Button(onClick = { press.click(); click() }, modifier = modifier.height(52.dp)
        .then(if (enabled) Modifier.immediateButtonPress(press) else Modifier), enabled = enabled,
        colors = (if (closing) colors.keepColorsWhenLocked() else colors).animateEnabled(enabled),
        shapes = ButtonDefaults.shapes(), interactionSource = press.native) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label, maxLines = 1)
    }
}
