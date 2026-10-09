@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.rememberBalancedWidths

/** Stable native ToggleButtons retain press ownership while theme/selection changes. */
@Composable
internal fun <T> SettingChoiceGroup(title:String,options:List<T>,value:T,label:(T)->String,onSelect:(T)->Unit,
    modifier:Modifier=Modifier) {
    val sources=remember(options) { options.map { MutableInteractionSource() } }
    // alpha01's animateWidth waits for >75% expansion on release. Drive weight directly,
    // without that threshold or adaptive content slots replacing a button mid-gesture.
    val weights=rememberBalancedWidths(options.map { 100f },sources,minimum=60f,
        expansion=100f*ButtonGroupDefaults.ExpandedRatio)
    Row(modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index,option -> key(option) {
            val select=feedbackClick { if(option!=value)onSelect(option) }
            val shapes=when(index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(checked=value==option,onCheckedChange={ if(it)select() },
                modifier=Modifier.weight(weights[index]).semantics {
                    contentDescription="$title：${label(option)}";selected=value==option
                },shapes=shapes,interactionSource=sources[index]) { Text(label(option),maxLines=1) }
        } }
    }
}
