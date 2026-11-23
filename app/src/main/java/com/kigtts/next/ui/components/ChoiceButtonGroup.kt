@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.semantics.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.LocalThemePaint
import com.kigtts.next.theme.themeFill

/** Connected M3E shapes share press expansion without replacing the active pointer owner. */
@Composable
internal fun <T> ChoiceButtonGroup(title:String,options:List<T>,value:T,label:(T)->String,onSelect:(T)->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,compactLabels:Boolean=false,preserveColorWhenLocked:Boolean=false,
    itemRole: Role = Role.RadioButton, fitContent: Boolean = false, icon: (@Composable (T) -> Unit)? = null) {
    val sources=remember(options) { options.map { MutableInteractionSource() } }
    val paint = LocalThemePaint.current?.takeIf { enabled }
    val minimumTouchHeight = LocalMinimumInteractiveComponentSize.current
    val motions=options.map { option -> key(option) { rememberChoicePressMotion() } }
    val weights=balancedButtonWidths(options.map { 100f },motions.map { it.progress.value },minimum=60f,
        expansion=100f*ButtonGroupDefaults.ExpandedRatio)
    ButtonGroup(modifier.then(if (fitContent) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()).selectableGroup(), expandedRatio = 0f,
        horizontalArrangement=Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index,option -> key(option) {
            val defaults=when {
                options.size == 1 -> ToggleButtonDefaults.shapes()
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val checked = value == option
            val selectionProgress by animateFloatAsState(if (checked) 1f else 0f,tween(160),label="choice-selection-shape")
            val shape=ChoiceButtonShape(defaults,selectionProgress,motions[index].progress.value)
            val fill = if (paint == null) Modifier else Modifier.themeFill(shape) { if (checked) it.primary else it.surfaceContainer }
            val defaultsColors = ToggleButtonDefaults.toggleButtonColors()
            val colors = when {
                paint != null -> defaultsColors.copy(containerColor = Color.Transparent, checkedContainerColor = Color.Transparent)
                preserveColorWhenLocked -> defaultsColors.keepColorsWhenLocked(checked)
                else -> defaultsColors
            }
            ToggleButton(checked=checked,onCheckedChange={ selected -> motions[index].commit { if (selected && !checked) onSelect(option) } },
                // Measure the native ripple and the animated fill at the same height.
                modifier=Modifier.then(if (fitContent) Modifier else Modifier.weight(weights[index])).heightIn(min = minimumTouchHeight)
                    .then(if(enabled)Modifier.choicePressFeedback(motions[index])else Modifier)
                    .then(if(preserveColorWhenLocked) Modifier else Modifier.enabledContentMotion(enabled)).then(fill).semantics {
                    contentDescription="$title：${label(option)}";selected=value==option;role=itemRole
                },enabled=enabled,shapes=ToggleButtonShapes(shape,shape,shape),interactionSource=sources[index],
                colors=colors,
                contentPadding=if(compactLabels)PaddingValues(horizontal=8.dp,vertical=8.dp)else ToggleButtonDefaults.ContentPadding) {
                icon?.invoke(option)
                if (icon != null) Spacer(Modifier.width(6.dp))
                val text = label(option)
                val style = MaterialTheme.typography.labelLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                val maxLines = if (compactLabels) 2 else 1
                if (paint == null) Text(text, maxLines = maxLines, style = style)
                else BasicText(text, style = style, maxLines = maxLines, color = ColorProducer {
                    val current = paint.palette.colors
                    if (checked) current.onPrimary else current.onSurfaceVariant
                })
            }
        } }
    }
}
