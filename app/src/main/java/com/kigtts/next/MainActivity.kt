package com.kigtts.next

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.cards.camera.CameraShutterKeys
import com.kigtts.next.ui.cards.camera.CameraShutterKeyOwner

class MainActivity : ComponentActivity(), CameraShutterKeyOwner {
    override val cameraShutterKeys = CameraShutterKeys()
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("quick-phrase-draft")?.let { draft ->
            if(model.content.restorePhraseDraft(draft)) {
                model.editQuickTexts()
                val restored = requireNotNull(model.content.phraseDraft.value)
                model.navigate(com.kigtts.next.state.Destination.PhraseEditor(restored.key, restored.phraseId == null))
            }
        }
        if(model.state.value.destination == com.kigtts.next.state.Destination.Home || model.state.value.destination is com.kigtts.next.state.Destination.PhraseEditor) {
            savedInstanceState?.getString("html-project-id")?.takeIf { model.mediaRepository.get(it)!=null }?.let { id ->
                model.navigate(com.kigtts.next.state.Destination.HtmlFiles(id,savedInstanceState.getString("html-project-target") ?: "display"))
            }
            savedInstanceState?.getString("content-editor-draft")?.let { token ->
                if(model.content.restoreEditorDraft(token))model.navigate(com.kigtts.next.state.Destination.ContentEditor(token))
            }
        }
        enableEdgeToEdge()
        setContent { KigttsApp(model) }
        if (savedInstanceState == null) model.sourceLink(intent?.data)
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); model.sourceLink(intent.data) }
    override fun onSaveInstanceState(outState: Bundle) {
        model.content.savedPhraseDraft()?.let { outState.putString("quick-phrase-draft",it) }
        model.content.savedEditorDraft()?.let { outState.putString("content-editor-draft",it) }
        (model.state.value.navigationStack+model.state.value.destination).filterIsInstance<com.kigtts.next.state.Destination.HtmlFiles>().lastOrNull()?.let {
            outState.putString("html-project-id",it.mediaId);outState.putString("html-project-target",it.target)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        cameraShutterKeys.dispatch(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        cameraShutterKeys.dispatch(event) || super.onKeyUp(keyCode, event)
    override fun onStop() { cameraShutterKeys.clearPressed(); if (!isChangingConfigurations) model.onBackground(); super.onStop() }
}
