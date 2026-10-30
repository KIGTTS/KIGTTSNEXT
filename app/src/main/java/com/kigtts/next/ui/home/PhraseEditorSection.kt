package com.kigtts.next.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.settings.SettingsGroup
import com.kigtts.next.ui.components.MotionIconButton

/** Form sections stay at their natural text size; only their containers change height. */
@Composable
internal fun PhraseEditorSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    SettingsGroup(title, modifier = modifier) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.animateContentSize(tween(220)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun PhraseAppearanceSection(expanded:Boolean,enabled:Boolean,toggle:()->Unit,content:@Composable ()->Unit) {
    SettingsGroup("卡片外观", headerAction = {
            MotionIconButton(feedbackClick(toggle),enabled=enabled,modifier=Modifier.size(40.dp).testTag("phrase-appearance-toggle")) {
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"收起外观选项"else"展开外观选项")
            }
        }) {
            AnimatedVisibility(expanded,enter=fadeIn(tween(180))+expandVertically(tween(240)),
                exit=fadeOut(tween(120))+shrinkVertically(tween(220))) {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) { content() }
                }
            }
    }
}
