@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.QuickCardType
import com.kigtts.next.ui.components.enabledContentMotion
import com.kigtts.next.ui.components.feedbackClick

/** None means no extra attachment; the shared subtitle can still carry rich formatting. */
@Composable
internal fun PhraseTypeTabs(type:QuickCardType,enabled:Boolean,select:(QuickCardType)->Unit) {
    PrimaryTabRow(type.ordinal,Modifier.testTag("phrase-type-tabs")) {
        QuickCardType.entries.forEach { option->Tab(option==type,feedbackClick { select(option) },enabled=enabled,
            modifier=Modifier.testTag("phrase-type-"+option.name).enabledContentMotion(enabled)) {
            Row(Modifier.height(48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Icon(when(option) {
                    QuickCardType.TEXT->Icons.Default.Block
                    QuickCardType.AUDIO->Icons.Default.AudioFile
                    QuickCardType.MEDIA->Icons.Default.PermMedia
                    QuickCardType.PAGE->Icons.Default.Language
                },null,Modifier.size(18.dp).testTag("phrase-type-icon-"+option.name))
                Text(option.label,maxLines=1)
            }
        } }
    }
}
