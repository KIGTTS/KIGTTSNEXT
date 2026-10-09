package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.feedbackClick

data class ToolbarAction(val id:String,val label:String,val icon:ImageVector,val enabled:Boolean=true,val click:()->Unit)
class PageToolbarActions {
    var source:State<List<ToolbarAction>>? by mutableStateOf(null)
}
val LocalPageToolbarActions=compositionLocalOf<PageToolbarActions?> { null }

/** Each retained page owns its registration. Callback updates never replace the whole scaffold. */
@Composable
fun RegisterPageActions(actions:List<ToolbarAction>) {
    val host=LocalPageToolbarActions.current ?: return
    val source=rememberUpdatedState(actions)
    DisposableEffect(host,source) {
        host.source=source
        onDispose { if(host.source===source)host.source=null }
    }
}

@Composable
fun RowScope.PageActionButtons(actions:List<ToolbarAction>) {
    var more by remember { mutableStateOf(false) }
    val active=LocalPageActive.current
    LaunchedEffect(active) { if(!active)more=false }
    actions.take(2).forEach { action -> key(action.id) {
        IconButton(feedbackClick(action.click),Modifier.testTag(action.id),enabled=active&&action.enabled) { Icon(action.icon,action.label) }
    } }
    if(actions.size>2) {
        IconButton(feedbackClick { more=true },enabled=active) { Icon(Icons.Default.MoreVert,"更多页面操作") }
        DropdownMenu(more&&active,{ more=false }) {
            actions.drop(2).forEach { action -> DropdownMenuItem(text={ Text(action.label) },leadingIcon={ Icon(action.icon,null) },
                enabled=action.enabled,onClick=feedbackClick { more=false;action.click() },modifier=Modifier.testTag(action.id)) }
        }
    }
}
