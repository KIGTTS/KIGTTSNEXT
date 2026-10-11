package com.kigtts.next.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.navigation.*

/** The same navigation host owns entry, exit, predictive back and retained form state. */
@Composable
fun PhraseEditorScreen(model: AppViewModel, token: String) {
    val current by model.content.phraseDraft.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    var retained by remember(token) { mutableStateOf(current?.takeIf { it.key == token }) }
    current?.takeIf { it.key == token }?.let { retained = it }
    val draft = retained ?: return
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = LocalPageActive.current
    RegisterPageActions(listOf(ToolbarAction("save-phrase-details", "保存", Icons.Default.Check,
        active && current?.key == token && draft.valid(model.mediaRepository)) {
        focus.clearFocus(); keyboard?.hide()
        model.content.savePhrase(); model.category(draft.category); model.navigateBack()
    }))
    PhraseDetailsForm(draft, categories, model, active && current?.key == token)
}
