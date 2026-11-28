package com.kigtts.next.ui.cards.camera

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.camera.expressions.ExpressionEffect
import com.kigtts.next.camera.expressions.ExpressionLibrary
import com.kigtts.next.camera.expressions.withBitmapArtwork
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.SaveChangesDialog
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterPageActions
import com.kigtts.next.ui.navigation.RegisterPageBackGuard
import com.kigtts.next.ui.navigation.RegisterPageLoading
import com.kigtts.next.ui.navigation.ToolbarAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CameraExpressionEditPage(model: AppViewModel, effectId: String?) {
    val context = LocalContext.current
    val library = remember(context) { ExpressionLibrary.get(context) }
    val options = rememberCameraOptions(loadWatermarks = false)
    val scope = rememberCoroutineScope()
    var original by remember(effectId) { mutableStateOf<ExpressionEffect?>(null) }
    var draft by remember(effectId) { mutableStateOf<ExpressionEffect?>(null) }
    var failed by remember(effectId) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmLeave by rememberSaveable(effectId) { mutableStateOf(false) }
    LaunchedEffect(library, effectId) {
        val opened = withContext(Dispatchers.IO) {
            runCatching {
                if (effectId == null) library.newDraft().withBitmapArtwork()
                else library.find(effectId)?.let(library::editable)
            }.getOrNull()
        }
        original = opened
        draft = opened
        failed = opened == null
    }
    val busy = saving || loading
    val canSave = draft?.name?.isNotBlank() == true && !busy
    val guardLeave = draft != original || busy
    fun save(done: () -> Unit) {
        val effect = draft?.takeIf { it.name.isNotBlank() } ?: return
        if (saving || loading) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { library.save(effect) }
                original = effect
                options.chooseExpression(effect.id)
                done()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                model.message(context.uiCopy("expression.file_invalid"))
            } finally {
                saving = false
            }
        }
    }
    fun requestBack() { if (!saving && !loading) confirmLeave = true }
    RegisterPageActions(listOf(ToolbarAction("camera-expression-save", uiText("action.save"), Icons.Default.Check, canSave) {
        save(model::navigateBack)
    }))
    RegisterPageLoading(busy || (draft == null && !failed))
    RegisterPageBackGuard(guardLeave, ::requestBack)
    PredictiveBackHandler(LocalPageActive.current && guardLeave && !confirmLeave) { progress ->
        try { progress.collect {}; requestBack() } catch (_: CancellationException) {}
    }
    if (failed) Text(uiText("expression.file_invalid"), Modifier.padding(20.dp))
    draft?.let { effect ->
        ExpressionDraftEditor(effect, saving, loading, { loading = it }, { draft = it }, stickers = model.stickers,
            save = { save(model::navigateBack) }, error = model::message)
    }
    if (confirmLeave) SaveChangesDialog(
        save = { finish -> save { finish(model::navigateBack) } },
        discard = { confirmLeave = false; model.navigateBack() },
        keepEditing = { confirmLeave = false },
        canSave = canSave, busy = busy,
    )
}
