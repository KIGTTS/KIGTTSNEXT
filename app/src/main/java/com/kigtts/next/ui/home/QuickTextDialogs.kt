package com.kigtts.next.ui.home

import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.kigtts.next.state.*

/** Keep the draft and entry history while the media picker temporarily covers the dialog. */
@Composable
fun PhraseDetailsDialog(draft: PhraseDraft, categories: List<String>, model: AppViewModel, dismiss: () -> Unit,
    anchor: PhraseDialogAnchor? = null, visible: Boolean = true) {
    var opened by rememberSaveable(draft.key) { mutableStateOf(false) }
    val source = remember(draft.key) { model.phrases.value.firstOrNull { it.id == draft.phraseId } }
    if (visible) {
        val restored = remember { opened }
        Dialog(onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
            val view = LocalView.current
            val light = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
            DisposableEffect(view, light) {
                val window = (view.parent as? DialogWindowProvider)?.window
                window?.setWindowAnimations(0)
                window?.setDimAmount(0f)
                if (window != null) {
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
                    }
                }
                onDispose { }
            }
            PhraseDialogTransform(anchor, source, restored, dismiss, model.mediaRepository) { close, enabled ->
                PhraseDetailsForm(draft, categories, model, enabled, close, dismiss)
            }
        }
        SideEffect { opened = true }
    }
}
