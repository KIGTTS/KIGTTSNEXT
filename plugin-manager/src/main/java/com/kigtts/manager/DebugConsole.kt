package com.kigtts.manager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun DebugConsole(controller:ManagerController,id:String,state:ManagerState,dismiss:()->Unit) {
    var action by remember { mutableStateOf("") };var inputs by remember { mutableStateOf("{}") };val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest=dismiss,title={ Text(if(id.isBlank())"操作记录" else "调试 · $id") },text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(id.isNotBlank()) {
                OutlinedTextField(action,{ action=it },label={ Text("动作 ID") },singleLine=true)
                OutlinedTextField(inputs,{ inputs=it },label={ Text("参数 JSON") })
                Button({ scope.launch { runCatching { controller.operation("execute",JSONObject().put("id",id).put("action",action).put("inputs",JSONObject(inputs))) }.onFailure { controller.report(it as? Exception ?: IllegalStateException(it.message)) } } },enabled=!state.busy&&action.isNotBlank()) { Text("执行") }
                Text(state.result,style=MaterialTheme.typography.bodySmall)
            } else Text(state.snapshot?.optJSONArray("logs")?.toString(2).orEmpty(),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ TextButton(dismiss) { Text("关闭") } })
}
