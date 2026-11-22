package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kigtts.next.media.MediaEntry
import com.kigtts.next.media.MediaKind
import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.media.displayLabel

/** Rare resource operations share one menu rather than compete with preview and playback. */
@Composable
internal fun PhraseResourceInfo(entry:MediaEntry,enabled:Boolean,canFrame:Boolean,frame:()->Unit,detach:()->Unit) {
    var menu by remember(entry.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (entry.kind == MediaKind.VIDEO || entry.kind == MediaKind.AUDIO) entry.kind.displayLabel() else entry.name,
            Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box {
            MotionIconButton(feedbackClick { menu=true },enabled=enabled,modifier=Modifier.testTag("phrase-resource-more")) { Icon(Icons.Default.MoreVert,uiText("accessibility.resource_options")) }
            DropdownMenu(menu&&enabled,{ menu=false }) {
                if(canFrame)DropdownMenuItem({ Text(uiText("quick.thumbnail_adjust")) },feedbackClick { menu=false;frame() })
                DropdownMenuItem({ Text(uiText("quick.remove_resource")) },feedbackClick { menu=false;detach() },modifier=Modifier.testTag("detach-phrase-media"))
            }
        }
    }
}
