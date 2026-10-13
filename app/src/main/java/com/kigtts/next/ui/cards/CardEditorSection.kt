package com.kigtts.next.ui.cards

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.homeCardShape

@Composable
internal fun CardEditorSection(title: String, icon: ImageVector, tag: String, forceExpanded: Boolean = false,
    content: @Composable () -> Unit) {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    LaunchedEffect(forceExpanded) { if (forceExpanded) expanded = true }
    Surface(shape = homeCardShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth()) {
            MotionTextButton(feedbackClick { expanded = !expanded }, Modifier.fillMaxWidth().testTag(tag)) {
                Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "收起" else "展开", Modifier.size(20.dp))
            }
            AnimatedVisibility(expanded, enter = fadeIn(tween(140)) + expandVertically(tween(200)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(180))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}
