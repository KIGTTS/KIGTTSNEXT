package com.kigtts.next.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.settings.SettingsGroup
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.animatedSettingShape

/** Form sections stay at their natural text size; only their containers change height. */
@Composable
internal fun PhraseEditorSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    SettingsGroup(title, modifier = modifier) {
        PhraseEditorRow(content = content)
    }
}

@Composable
internal fun PhraseEditorRow(position: SettingPosition = SettingPosition.SINGLE, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = animatedSettingShape(position, MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.animateContentSize(tween(220)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
