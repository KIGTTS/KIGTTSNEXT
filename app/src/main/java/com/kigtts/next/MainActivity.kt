package com.kigtts.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.KigttsApp

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("quick-phrase-draft")?.let { draft ->
            if(model.content.restorePhraseDraft(draft))model.editQuickTexts()
        }
        enableEdgeToEdge()
        setContent { KigttsApp(model) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        model.content.savedPhraseDraft()?.let { outState.putString("quick-phrase-draft",it) }
        super.onSaveInstanceState(outState)
    }
    override fun onStop() { if (!isChangingConfigurations) model.onBackground(); super.onStop() }
}
