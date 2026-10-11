package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kigtts.next.media.MediaEntry
import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.feedbackClick

/** Rare resource operations share one menu rather than compete with preview and playback. */
@Composable
internal fun PhraseResourceInfo(entry:MediaEntry,enabled:Boolean,canFrame:Boolean,frame:()->Unit,detach:()->Unit) {
    var menu by remember(entry.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(entry.name,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box {
            MotionIconButton(feedbackClick { menu=true },enabled=enabled,modifier=Modifier.testTag("phrase-resource-more")) { Icon(Icons.Default.MoreVert,"资源选项") }
            DropdownMenu(menu&&enabled,{ menu=false }) {
                if(canFrame)DropdownMenuItem({ Text("调整缩略图") },feedbackClick { menu=false;frame() })
                DropdownMenuItem({ Text("移除资源") },feedbackClick { menu=false;detach() },modifier=Modifier.testTag("detach-phrase-media"))
            }
        }
    }
}
