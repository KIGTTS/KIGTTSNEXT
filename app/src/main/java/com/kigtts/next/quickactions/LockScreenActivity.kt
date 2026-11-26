package com.kigtts.next.quickactions

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.i18n.UiLanguageStore
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettingsStore

internal class LockScreenDraft : ViewModel() {
    // Rotation retains the draft; process death and closing the temporary page discard it.
    var text by mutableStateOf("")
}

class LockScreenActivity : ComponentActivity() {
    private val draft: LockScreenDraft by viewModels()
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private var target: LockScreenTarget? by mutableStateOf(null)
    private var error: String? by mutableStateOf(null)
    private val reader by lazy { LockScreenReader(this) { error = "quick_actions.lock_tts_unavailable" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!QuickActionPreferences(this).lockScreenShortcut) {
            finish()
            return
        }
        if (!keyguard.isKeyguardLocked && !keyguard.isDeviceLocked) {
            launchMain(LockScreenTarget.HOME)
            return
        }
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val theme = ThemeSettingsStore(this).load().copy(background = null)
        val locale = UiLanguageStore(this).load()
        setContent {
            CompositionLocalProvider(LocalUiLocale provides locale) {
                KigttsTheme(theme) {
                    LockScreenPanel(draft.text, { draft.text = it }, reader.reading, target != null, error,
                        close = ::finish, read = { error = null; reader.toggle(draft.text, locale) }, open = ::unlock)
                }
            }
        }
    }

    private fun unlock(route: LockScreenTarget) {
        if (target != null) return
        if (!keyguard.isKeyguardLocked && !keyguard.isDeviceLocked) {
            launchMain(route)
            return
        }
        target = route
        error = null
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                target = null
                if (!isFinishing && !isDestroyed && !keyguard.isDeviceLocked) launchMain(route)
            }
            override fun onDismissCancelled() { target = null }
            override fun onDismissError() { target = null; error = "quick_actions.lock_unlock_failed" }
        })
    }

    private fun launchMain(route: LockScreenTarget) {
        startActivity(LockScreenShortcut.mainIntent(this, route))
        finish()
    }

    override fun onStop() {
        reader.stop()
        super.onStop()
    }
    override fun onDestroy() {
        reader.close()
        super.onDestroy()
    }
}

@Composable
private fun LockScreenPanel(text: String, change: (String) -> Unit, reading: Boolean, unlocking: Boolean, error: String?,
    close: () -> Unit, read: () -> Unit, open: (LockScreenTarget) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(topBar = {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, uiText("action.close")) }
                Text(uiText("quick_actions.lock_notification_title"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({ keyboard?.hide(); focus.clearFocus(); open(LockScreenTarget.ALBUM) }, enabled = !unlocking,
                    modifier = Modifier.testTag("lock-screen-album")) { Icon(Icons.Default.PhotoLibrary, uiText("nav.album")) }
                IconButton({ keyboard?.hide(); focus.clearFocus(); open(LockScreenTarget.SETTINGS) }, enabled = !unlocking,
                    modifier = Modifier.testTag("lock-screen-settings")) { Icon(Icons.Default.Settings, uiText("nav.settings")) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { if (it.length <= android.speech.tts.TextToSpeech.getMaxSpeechInputLength()) change(it) },
                    Modifier.weight(1f).fillMaxWidth().testTag("lock-screen-draft"),
                    textStyle = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
                    placeholder = { Text(uiText("quick_actions.lock_draft"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
                    shape = MaterialTheme.shapes.large)
                error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ change(""); if (reading) read() }, Modifier.weight(1f), enabled = text.isNotEmpty()) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text(uiText("action.clear"))
                    }
                    Button({ keyboard?.hide(); focus.clearFocus(); read() }, Modifier.weight(1f), enabled = reading || text.isNotBlank()) {
                        Icon(if (reading) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(uiText(if (reading) "subtitle.stop" else "subtitle.read_aloud"))
                    }
                }
            }
        }
    }
}
