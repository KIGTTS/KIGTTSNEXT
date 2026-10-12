package com.kigtts.next.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.QuickTextReply
import kotlinx.coroutines.launch

@Composable
fun QuickTextReviewScreen(model:AppViewModel,token:String) {
    val preview=model.quickTextPlugins.plan(token)
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();RegisterPageLoading(busy)
    RegisterPageActions(listOf(ToolbarAction("apply-quick-text-batch","确认应用修改",Icons.Default.Check,preview!=null&&!busy) {
        if(!busy) {busy=true;scope.launch {try {when(val result=model.quickTextPlugins.approve(token)) {
            is QuickTextReply.Applied->model.navigateBack()
            is QuickTextReply.Failure->error=result.message
            else->error="无法提交修改"
        }} finally {busy=false}}}
    }))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("quick-text-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(preview==null)Text("预览已失效，请让插件重新准备。") else {
            Text("新增 ${preview.added} 项 · 更新 ${preview.updated} 项 · 删除 ${preview.deleted} 项",style=MaterialTheme.typography.titleMedium)
            Text("分组变更：${preview.groupChanges}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(preview.requiresConfirmation)Text("此操作包含删除。确认后将应用整批修改。",color=MaterialTheme.colorScheme.error)
            preview.titles.forEach {title->Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainer) {Text(title,Modifier.fillMaxWidth().padding(16.dp))}}
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
