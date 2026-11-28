package com.kigtts.next.ui.cards.camera

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.*
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.next.ui.management.ManagedItemList
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.ToolbarAction
import com.kigtts.plugin.api.ManagementItem
import com.kigtts.plugin.api.ManagementList
import com.kigtts.plugin.api.PageAction
import com.kigtts.plugin.api.PluginIcon
import com.kigtts.plugin.api.PluginJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun CameraExpressionManagementPage(model: AppViewModel) {
    val context = LocalContext.current
    val library = remember(context) { ExpressionLibrary.get(context) }
    val options = rememberCameraOptions(loadWatermarks = false)
    val revision by library.revision.collectAsState()
    val choices by produceState<List<ExpressionEffect>?>(null, library, revision, LocalConfiguration.current) {
        value = withContext(Dispatchers.IO) { library.choices() }
    }
    val effects = remember(choices) { choices.orEmpty().associateBy { it.id } }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun run(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { task() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.message(context.uiCopy("expression.file_invalid")) }
            finally { busy = false }
        }
    }

    val importPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val selected = withContext(Dispatchers.IO) {
                val file = File.createTempFile("expression-", ".kigexpression", context.cacheDir)
                try {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        file.writeBytes(AppearanceAssets.readBounded(it, ExpressionPack.MAX_PACK))
                    }
                    library.install(file)
                } finally { file.delete() }
            }
            options.chooseExpression(selected)
            model.message(context.uiCopy("expression.installed"))
        }
    }
    RegisterPageActions(listOf(
        ToolbarAction("camera-expression-new", uiText("expression.new"), Icons.Default.Add, !busy) {
            model.navigate(Destination.CameraExpressionEditor())
        },
        ToolbarAction("camera-expression-import", uiText("expression.import"), Icons.Default.FileOpen, !busy) {
            importPack.launch(arrayOf("*/*"))
        },
        ToolbarAction("camera-expression-market", uiText("expression.store"), Icons.Default.Storefront, !busy, click = model::openExpressionStore),
    ))
    val apply = uiText("expression.apply")
    val share = uiText("expression.share")
    val remove = uiText("expression.remove")
    val selected = options.expressionId
    val items = remember(effects, selected, apply, share, remove) {
        effects.values.map { effect ->
            val id = effect.id
            ManagementItem(id = id, title = effect.name, primaryAction = "edit/$id", actions = buildList {
                if (id != selected) add(PageAction("apply/$id", apply, PluginIcon.NONE, symbol = "check"))
                add(PageAction("share/$id", share, PluginIcon.EXPORT))
                if (id.startsWith("local.")) add(PageAction("remove/$id", remove, PluginIcon.NONE, symbol = "delete"))
            })
        }
    }
    ManagedItemList(ManagementList("camera-face-stickers", items, "reorder"), busy || choices == null, invoke = { action, inputs ->
        val id = action.substringAfter('/')
        when (action.substringBefore('/')) {
            "reorder" -> run {
                val order = PluginJson.decodeFromString<List<String>>(requireNotNull(inputs["order"]))
                withContext(Dispatchers.IO) { library.reorder(order) }
            }
            "edit" -> model.navigate(Destination.CameraExpressionEditor(id))
            "apply" -> options.chooseExpression(id)
            "share" -> effects[id]?.let { effect -> run {
                context.startActivity(withContext(Dispatchers.IO) { library.share(effect) })
            } }
            "remove" -> run {
                withContext(Dispatchers.IO) { library.remove(id) }
                if (options.expressionId == id) options.chooseExpression(null)
            }
        }
    }, leading = { item ->
        val thumbnail = effects[item.id]?.let { expressionThumbnail(it) }
        Box(Modifier.size(44.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize())
            else Icon(Icons.Default.Face, null)
            if (item.id == selected) Icon(Icons.Default.CheckCircle, uiText("accessibility.select_item", "title" to item.title),
                Modifier.align(androidx.compose.ui.Alignment.BottomEnd).size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }) {
        TextButton({ options.chooseExpression(null) }, enabled = selected != null && !busy) {
            Icon(Icons.Default.Block, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(uiText("option.off"))
        }
    }
}
