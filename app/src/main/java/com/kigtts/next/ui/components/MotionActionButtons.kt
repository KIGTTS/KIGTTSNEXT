@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp

@Composable
fun MotionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), interactionSource: MutableInteractionSource? = null,
    transientLock: Boolean = false,
    content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val press = rememberImmediateButtonPress(source)
    LaunchedEffect(enabled) { if (!enabled && !transientLock) press.cancel() }
    IconButton(onClick = { press.click(); onClick() }, modifier = modifier.then(if(enabled) Modifier.immediateButtonPress(press) else Modifier),
        enabled = enabled, colors = colors.animateEnabled(enabled), interactionSource = press.native,
        shapes = IconButtonDefaults.shapes(), content = content)
}

@Composable
fun MotionIconToggleButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, transientLock: Boolean = false, content: @Composable () -> Unit) {
    val press = rememberImmediateButtonPress(remember { MutableInteractionSource() })
    LaunchedEffect(enabled) { if (!enabled && !transientLock) press.cancel() }
    val colors = IconButtonDefaults.iconToggleButtonColors().let { colors -> if (transientLock) colors.copy(
        disabledContainerColor = if (checked) colors.checkedContainerColor else colors.containerColor,
        disabledContentColor = if (checked) colors.checkedContentColor else colors.contentColor) else colors }
    IconToggleButton(checked, feedbackChange { value -> press.click(); onCheckedChange(value) },
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier.then(if (enabled) Modifier.immediateButtonPress(press) else Modifier),
        enabled = enabled, interactionSource = press.native, colors = colors, content = content)
}

@Composable
fun MotionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), content: @Composable RowScope.() -> Unit) {
    val press = rememberImmediateButtonPress(remember { MutableInteractionSource() })
    LaunchedEffect(enabled) { if (!enabled) press.cancel() }
    TextButton(onClick = { press.click(); onClick() }, modifier = modifier.then(if(enabled) Modifier.immediateButtonPress(press) else Modifier), enabled = enabled,
        colors = colors.animateEnabled(enabled), interactionSource = press.native, shapes = ButtonDefaults.shapes(), content = content)
}

@Composable
fun MotionOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val active = ButtonDefaults.outlinedButtonBorder(true)
    val disabled = ButtonDefaults.outlinedButtonBorder(false)
    val border = BorderStroke(active.width, lerp((disabled.brush as SolidColor).value,
        (active.brush as SolidColor).value, enabledProgress(enabled)))
    val press = rememberImmediateButtonPress(remember { MutableInteractionSource() })
    LaunchedEffect(enabled) { if (!enabled) press.cancel() }
    OutlinedButton(onClick = { press.click(); onClick() }, modifier = modifier.then(if(enabled) Modifier.immediateButtonPress(press) else Modifier), enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors().animateEnabled(enabled), interactionSource = press.native, shapes = ButtonDefaults.shapes(),
        border = border, content = content)
}
