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
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.feedbackClick

/** Form sections stay at their natural text size; only their containers change height. */
@Composable
internal fun PhraseEditorSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.animateContentSize(tween(220)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun PhraseAppearanceSection(expanded:Boolean,enabled:Boolean,toggle:()->Unit,content:@Composable ()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            MotionTextButton(feedbackClick(toggle),enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("phrase-appearance-toggle")) {
                Icon(Icons.Default.Palette,null);Spacer(Modifier.width(8.dp));Text("卡片外观",Modifier.weight(1f))
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"收起外观选项"else"展开外观选项")
            }
            AnimatedVisibility(expanded,enter=fadeIn(tween(180))+expandVertically(tween(240)),
                exit=fadeOut(tween(120))+shrinkVertically(tween(220))) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}
