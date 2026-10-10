@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.animation.core.*
import com.kigtts.next.ui.components.*

/** Stable native ToggleButtons retain press ownership while theme/selection changes. */
@Composable
internal fun <T> SettingChoiceGroup(title:String,options:List<T>,value:T,label:(T)->String,onSelect:(T)->Unit,
    modifier:Modifier=Modifier) {
    val sources=remember(options) { options.map { MutableInteractionSource() } }
    val motions=options.map { option -> key(option) { rememberChoicePressMotion() } }
    val weights=balancedButtonWidths(options.map { 100f },motions.map { it.progress.value },minimum=60f,
        expansion=100f*ButtonGroupDefaults.ExpandedRatio)
    Row(modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index,option -> key(option) {
            val defaults=when(index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val selectionProgress by animateFloatAsState(if (value==option) 1f else 0f,tween(160),label="choice-selection-shape")
            val shape=ConnectedChoiceShape(defaults,selectionProgress,motions[index].progress.value)
            ToggleButton(checked=value==option,onCheckedChange={ checked -> motions[index].commit { if(checked&&option!=value)onSelect(option) } },
                modifier=Modifier.weight(weights[index]).choicePressFeedback(motions[index]).semantics {
                    contentDescription="$title：${label(option)}";selected=value==option
                },shapes=ToggleButtonShapes(shape,shape,shape),interactionSource=sources[index]) { Text(label(option),maxLines=1) }
        } }
    }
}
