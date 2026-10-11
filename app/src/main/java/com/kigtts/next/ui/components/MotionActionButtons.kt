@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp

@Composable
fun MotionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors.animateEnabled(enabled), interactionSource = interactionSource, content = content)
}

@Composable
fun MotionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier, enabled, colors = colors.animateEnabled(enabled), content = content)
}

@Composable
fun MotionOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val active = ButtonDefaults.outlinedButtonBorder(true)
    val disabled = ButtonDefaults.outlinedButtonBorder(false)
    val border = BorderStroke(active.width, lerp((disabled.brush as SolidColor).value,
        (active.brush as SolidColor).value, enabledProgress(enabled)))
    OutlinedButton(onClick, modifier, enabled, colors = ButtonDefaults.outlinedButtonColors().animateEnabled(enabled),
        border = border, content = content)
}
