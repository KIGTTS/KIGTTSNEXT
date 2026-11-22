@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.media.code

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.kigtts.next.ui.components.*

@Composable
internal fun CodeShortcutBar(state:CodeEditorState,enabled:Boolean,retainFocus:()->Unit,invoke:(CodeKey)->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().testTag("code-shortcuts")) {
        Row(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),verticalAlignment=Alignment.Top) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val columns=(maxWidth.value/48).toInt().coerceAtLeast(1)
                if(expanded)Column { CodeKey.entries.chunked(columns).forEach { keys ->
                    Row { keys.forEach { CodeShortcut(it,state.available(it)&&enabled,invoke) } }
                } } else Row(Modifier.horizontalScroll(rememberScrollState())) { CodeKey.entries.forEach { CodeShortcut(it,state.available(it)&&enabled,invoke) } }
            }
            MotionIconButton(feedbackClick { expanded=!expanded;retainFocus() },Modifier.testTag("code-expand"),enabled=enabled) {
                Icon(if(expanded)Icons.Default.ExpandMore else Icons.Default.ExpandLess,if(expanded)uiText("accessibility.code_shortcuts_collapse") else uiText("accessibility.code_shortcuts_expand"))
            }
        }
    }
}

@Composable
private fun CodeShortcut(key:CodeKey,enabled:Boolean,invoke:(CodeKey)->Unit) {
    MotionIconButton(feedbackClick { invoke(key) },Modifier.size(48.dp).testTag("code-key/"+key.name),enabled=enabled) {
        val icon=when(key) {
            CodeKey.UNDO->Icons.AutoMirrored.Filled.Undo;CodeKey.REDO->Icons.AutoMirrored.Filled.Redo
            CodeKey.LEFT->Icons.AutoMirrored.Filled.ArrowBack;CodeKey.RIGHT->Icons.AutoMirrored.Filled.ArrowForward
            CodeKey.UP->Icons.Default.ArrowUpward;CodeKey.DOWN->Icons.Default.ArrowDownward
            CodeKey.HOME->Icons.Default.FirstPage;CodeKey.END->Icons.AutoMirrored.Filled.LastPage;CodeKey.TAB->Icons.AutoMirrored.Filled.KeyboardTab
            else->null
        }
        if(icon!=null)Icon(icon,key.label) else Text(key.insertion.orEmpty(),style=MaterialTheme.typography.titleMedium,
            modifier=Modifier.then(Modifier.semanticsLabel(key.label)))
    }
}

private fun Modifier.semanticsLabel(label:String)=this.then(Modifier.semantics { contentDescription=label })
