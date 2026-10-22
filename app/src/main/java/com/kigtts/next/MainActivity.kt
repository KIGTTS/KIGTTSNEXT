package com.kigtts.next

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.notificationLink
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.cards.camera.CameraShutterKeys
import com.kigtts.next.ui.cards.camera.CameraShutterKeyOwner

class MainActivity : ComponentActivity(), CameraShutterKeyOwner {
    override val cameraShutterKeys = CameraShutterKeys()
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The retained ViewModel keeps the current session during rotation. A new process
        // starts on Home instead of reopening an editor or project from an old task bundle.
        enableEdgeToEdge()
        setContent { KigttsApp(model) }
        if (savedInstanceState == null) { model.sourceLink(intent?.data); model.notificationLink(intent) }
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); model.sourceLink(intent.data); model.notificationLink(intent) }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        cameraShutterKeys.dispatch(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        cameraShutterKeys.dispatch(event) || super.onKeyUp(keyCode, event)
    override fun onStop() { cameraShutterKeys.clearPressed(); if (!isChangingConfigurations) model.onBackground(); super.onStop() }
}
