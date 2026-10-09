package com.kigtts.manager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@Composable
fun ManagerContent(controller:ManagerController,state:ManagerState,modifier:Modifier=Modifier) {
    val scope=rememberCoroutineScope();var approval by remember { mutableStateOf<Pair<File,JSONObject>?>(null) }
    var selected by remember { mutableStateOf<JSONObject?>(null) };var console by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    fun run(command:String,args:JSONObject=JSONObject()) { scope.launch { runCatching { controller.operation(command,args) } } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch {
        var file:File?=null
        try { file=controller.import(uri);val preview=controller.operation("inspect",file=file);approval=file to preview }
        catch(error:Exception) { controller.report(error);file?.delete() }
    } }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({ picker.launch(arrayOf("*/*")) },enabled=!state.busy) { Text("安装插件") }
            FilledTonalButton({ run("refresh") },enabled=!state.busy) { Text("刷新") }
            TextButton({ console="" },enabled=!state.busy) { Text("操作记录") }
        }
        val array=state.snapshot?.optJSONArray("plugins")
        val plugins=if(array==null)emptyList() else (0 until array.length()).map { array.getJSONObject(it) }
        LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            items(plugins,key={ it.getString("id") }) { plugin ->
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(plugin.getString("name"),style=MaterialTheme.typography.titleMedium)
                            Text(plugin.getString("id"),style=MaterialTheme.typography.labelSmall)
                            Text("${plugin.getString("version")} · "+when { plugin.optBoolean("builtin")->"内置";!plugin.optBoolean("enabled")->"已停用";plugin.optBoolean("loaded")->"运行中 · ${plugin.optInt("pid")}";else->"未加载" },style=MaterialTheme.typography.bodySmall)
                            plugin.optString("error").takeIf(String::isNotBlank)?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                        }
                        IconButton({ selected=plugin },enabled=!state.busy) { Icon(Icons.Default.MoreVert,"管理 "+plugin.getString("name")) }
                    }
                }
            }
        }
    }
    approval?.let { (file,preview) -> AlertDialog(onDismissRequest={ approval=null;file.delete() },title={ Text("安装 "+preview.getString("name")) },
        text={ Column { Text("版本 "+preview.getString("version"));Text("发布者签名");Text(preview.getString("publisher"),style=MaterialTheme.typography.labelSmall)
            Text(if(preview.optBoolean("knownPublisher"))"来自已使用的发布者" else "仅安装你信任的发布者提供的插件") } },
        confirmButton={ TextButton({ approval=null;scope.launch { try { controller.operation("install",JSONObject().put("publisher",preview.getString("publisher")),file) } catch(error:Exception) { controller.report(error) } finally { file.delete() } } }) { Text("安装") } },
        dismissButton={ TextButton({ approval=null;file.delete() }) { Text("取消") } }) }
    selected?.let { plugin -> val id=plugin.getString("id")
        AlertDialog(onDismissRequest={ selected=null },title={ Text(plugin.getString("name")) },text={ Column {
            if(!plugin.optBoolean("builtin")) {
                TextButton({ selected=null;run("enable",JSONObject().put("id",id).put("enabled",!plugin.optBoolean("enabled"))) }) { Text(if(plugin.optBoolean("enabled"))"停用" else "启用") }
                TextButton({ selected=null;run("restart",JSONObject().put("id",id)) }) { Text("重载") }
                TextButton({ selected=null;deleting=id }) { Text("卸载",color=MaterialTheme.colorScheme.error) }
            }
            TextButton({ selected=null;console=id }) { Text("调试动作") }
            if(plugin.optJSONArray("capabilities")?.toString()?.contains("tts")==true||id=="service.speech-synthesis")
                TextButton({ selected=null;run("tts-test",JSONObject().put("id",id)) }) { Text("合成自检") }
        } },confirmButton={ TextButton({ selected=null }) { Text("关闭") } })
    }
    deleting?.let { id -> AlertDialog(onDismissRequest={ deleting=null },title={ Text("卸载插件？") },text={ Text("该插件的模型、配置和外置资源也会删除。") },
        confirmButton={ TextButton({ deleting=null;run("uninstall",JSONObject().put("id",id).put("confirmed",true)) }) { Text("卸载") } },dismissButton={ TextButton({ deleting=null }) { Text("取消") } }) }
    console?.let { id -> DebugConsole(controller,id,state) { console=null } }
}
