@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** Standard M3E group: each button keeps its own corners and shares press expansion with neighbors. */
@Composable
internal fun <T> ChoiceButtonGroup(title:String,options:List<T>,value:T,label:(T)->String,onSelect:(T)->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,compactLabels:Boolean=false,preserveColorWhenLocked:Boolean=false,
    itemRole: Role = Role.RadioButton) {
    val sources=remember(options) { options.map { MutableInteractionSource() } }
    val motions=options.map { option -> key(option) { rememberChoicePressMotion() } }
    val weights=balancedButtonWidths(options.map { 100f },motions.map { it.progress.value },minimum=60f,
        expansion=100f*ButtonGroupDefaults.ExpandedRatio)
    ButtonGroup(modifier.fillMaxWidth().selectableGroup(), expandedRatio = 0f,
        horizontalArrangement=ButtonGroupDefaults.HorizontalArrangement) {
        options.forEachIndexed { index,option -> key(option) {
            val defaults=ToggleButtonDefaults.shapes()
            val selectionProgress by animateFloatAsState(if (value==option) 1f else 0f,tween(160),label="choice-selection-shape")
            val shape=ChoiceButtonShape(defaults,selectionProgress,motions[index].progress.value)
            ToggleButton(checked=value==option,onCheckedChange={ checked -> motions[index].commit { if(checked&&option!=value)onSelect(option) } },
                modifier=Modifier.weight(weights[index]).then(if(enabled)Modifier.choicePressFeedback(motions[index])else Modifier)
                    .then(if(preserveColorWhenLocked) Modifier else Modifier.enabledContentMotion(enabled)).semantics {
                    contentDescription="$title：${label(option)}";selected=value==option;role=itemRole
                },enabled=enabled,shapes=ToggleButtonShapes(shape,shape,shape),interactionSource=sources[index],
                colors=ToggleButtonDefaults.toggleButtonColors().let { if(preserveColorWhenLocked)it.keepColorsWhenLocked(value==option)else it },
                contentPadding=if(compactLabels)PaddingValues(horizontal=8.dp,vertical=8.dp)else ToggleButtonDefaults.ContentPadding) {
                Text(label(option),maxLines=if(compactLabels)2 else 1,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize=if(compactLabels)12.sp else androidx.compose.ui.unit.TextUnit.Unspecified)
            }
        } }
    }
}
