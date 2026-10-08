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
        enableEdgeToEdge()
        setContent { KigttsApp(model) }
    }
    override fun onStop() { if (!isChangingConfigurations) model.onBackground(); super.onStop() }
}
