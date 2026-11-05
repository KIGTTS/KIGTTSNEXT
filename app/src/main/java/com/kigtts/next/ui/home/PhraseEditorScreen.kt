package com.kigtts.next.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.PhraseDraftState
import com.kigtts.next.ui.components.SaveChangesDialog
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.CancellationException

/** The same navigation host owns entry, exit, predictive back and retained form state. */
@Composable
fun PhraseEditorScreen(model: AppViewModel, token: String) {
    com.kigtts.next.ui.components.ProvideIconTextContextMenu { PhraseEditorContent(model, token) }
}

@Composable
private fun PhraseEditorContent(model: AppViewModel, token: String) {
    val current by model.content.phraseDraft.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val saving by model.content.phraseSavePending.collectAsStateWithLifecycle()
    val completed by model.content.phraseSaveCompleted.collectAsStateWithLifecycle()
    var retained by remember(token) { mutableStateOf(current?.takeIf { it.key == token }) }
    current?.takeIf { it.key == token }?.let { retained = it }
    val draft = retained ?: return
    var baseline by rememberSaveable(token) { mutableStateOf(PhraseDraftState.write(draft)) }
    val serialized = PhraseDraftState.write(draft)
    val dirty = serialized != baseline
    var discard by rememberSaveable(token) { mutableStateOf(false) }
    var error by rememberSaveable(token) { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = LocalPageActive.current
    val valid = current?.key == token && draft.valid(model.mediaRepository)
    fun saveDraft(waitForExit: Boolean = false) {
        if (!active || saving || !valid) return
        error = null
        try {
            focus.clearFocus(); keyboard?.hide()
            check(model.content.savePhraseForExit(waitForExit)) { "保存未完成，请检查内容与分组后重试" }
            baseline = serialized
            model.category(draft.category)
        } catch (failure: Exception) {
            error = failure.message ?: "保存失败，请重试"
            discard = true
        }
    }
    RegisterPageBackGuard(active && (dirty || saving)) { if (!saving) discard = true }
    PredictiveBackHandler(active && (dirty || saving) && !discard) { events ->
        try { events.collect { }; if (!saving) discard = true } catch (_: CancellationException) { }
    }
    RegisterPageActions(listOf(ToolbarAction("share-phrase-details", "分享", Icons.Default.Share,
        active && !saving && valid) { model.sharing.shareDraft(draft) }, ToolbarAction("save-phrase-details", "保存", Icons.Default.Check,
        active && !saving && valid) { saveDraft() }))
    PhraseDetailsForm(draft, categories, model, active && !saving && current?.key == token)
    if (active && discard) SaveChangesDialog(
        save = { _ -> saveDraft(waitForExit = true) },
        discard = { focus.clearFocus(); keyboard?.hide(); discard = false; model.content.cancelPhrase(); model.navigateBack() },
        keepEditing = { discard = false }, canSave = valid, busy = saving, error = error,
        completed = if(completed) ({if(model.content.cleanupPhraseSave(token))discard=false}) else null)
}
