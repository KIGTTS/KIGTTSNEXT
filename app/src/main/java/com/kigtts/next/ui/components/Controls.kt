@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.*
import com.kigtts.plugin.api.PluginIcon

fun PluginIcon.vector(): ImageVector = when (this) {
    PluginIcon.INFO -> Icons.Default.Info
    PluginIcon.MIC -> Icons.Default.Mic
    PluginIcon.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
    PluginIcon.ADD -> Icons.Default.Add
    PluginIcon.MENU -> Icons.Default.MoreHoriz
    PluginIcon.SETTINGS -> Icons.Default.Tune
    PluginIcon.TEXT -> Icons.Default.TextFields
    PluginIcon.STAR -> Icons.Default.Star
    PluginIcon.HEARING -> Icons.Default.Hearing
    PluginIcon.CLOSE -> Icons.Default.Close
    PluginIcon.PIN -> Icons.Default.PushPin
    PluginIcon.EDIT -> Icons.Default.Edit
    PluginIcon.IMAGE -> Icons.Default.Image
    PluginIcon.EXPORT -> Icons.Default.FileUpload
}

@Composable
fun ToolButton(
    label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shapes: IconButtonShapes = IconButtonDefaults.shapes(
        shape = RoundedCornerShape(16.dp), pressedShape = RoundedCornerShape(8.dp)),
    colors: IconButtonColors = compactTonalIconColors(),
    size: Dp = 32.dp,
) {
    val press=rememberImmediateButtonPress(interactionSource)
    FilledTonalIconButton(onClick = feedbackClick { press.click();onClick() }, modifier = modifier.immediateButtonPress(press).height(size).widthIn(min = size)
        .semantics { contentDescription = label },
        shapes = shapes, interactionSource = press.native, colors = colors) {
        AnimatedButtonContent(icon) { Icon(it, contentDescription = null, modifier = Modifier.size(size / 2)) }
    }
}

@Composable
fun PillButton(
    label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shapes: ButtonShapes = expressivePillShapes(),
    colors: ButtonColors = compactTonalButtonColors(),
) {
    val press=rememberImmediateButtonPress(interactionSource)
    FilledTonalButton(onClick = feedbackClick { press.click();onClick() }, modifier = modifier.immediateButtonPress(press).height(32.dp),
        shapes = shapes, interactionSource = press.native, colors = colors,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        AnimatedButtonContent(icon to label) { (image, text) ->
            Row {
                Icon(image, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(text, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun expressivePillShapes() = ButtonDefaults.shapes(
    shape = RoundedCornerShape(100.dp), pressedShape = RoundedCornerShape(8.dp))
