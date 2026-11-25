package com.kigtts.next

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.notificationLink
import com.kigtts.next.overlay.overlayLink
import com.kigtts.next.state.HomePanel
import com.kigtts.next.ui.onboarding.FirstRunHost
import com.kigtts.next.ui.onboarding.FirstRunStep
import com.kigtts.next.ui.onboarding.FirstRunStore
import com.kigtts.next.ui.onboarding.NativeStartup
import com.kigtts.next.ui.onboarding.WelcomeIllustration
import com.kigtts.next.ui.cards.camera.CameraShutterKeys
import com.kigtts.next.ui.cards.camera.CameraShutterKeyOwner

private const val PENDING_LAUNCH = "pending-launch"

class MainActivity : ComponentActivity(), CameraShutterKeyOwner {
    override val cameraShutterKeys = CameraShutterKeys()
    private val model: AppViewModel by viewModels()
    private val onboarding by lazy {
        @Suppress("DEPRECATION")
        val install = packageManager.getPackageInfo(packageName, 0)
        val existing = install.lastUpdateTime > install.firstInstallTime && getSharedPreferences("quick-phrases", 0).contains("items")
        FirstRunStore(this, existingInstall = existing)
    }
    private var pendingLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KigttsNext)
        super.onCreate(savedInstanceState)
        // The retained ViewModel keeps the current session during rotation. A new process
        // starts on Home instead of reopening an editor or project from an old task bundle.
        enableEdgeToEdge()
        val progress = onboarding
        if (progress.step() == FirstRunStep.WELCOME) WelcomeIllustration.prepare(application)
        val startup = NativeStartup(this)
        pendingLaunch = savedInstanceState?.getBoolean(PENDING_LAUNCH) ?: true
        setContent { FirstRunHost(model, progress, startup, complete = ::finishOnboarding, exit = ::finish) }
        if (savedInstanceState == null) startup.install()
        deliverLaunch()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PENDING_LAUNCH, pendingLaunch)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLaunch = true
        deliverLaunch()
    }
    private fun deliverLaunch() {
        if (!pendingLaunch || onboarding.step() != FirstRunStep.COMPLETE) return
        pendingLaunch = false
        model.sourceLink(intent?.data)
        model.externalIntent(intent)
        model.notificationLink(intent)
        model.overlayLink(intent)
    }
    private fun finishOnboarding(tryQuickText: Boolean) {
        if (tryQuickText && (intent?.action == Intent.ACTION_MAIN || intent?.action == null)) model.openPanel(HomePanel.QUICK_TEXT)
        deliverLaunch()
    }
    override fun onStart() {
        super.onStart()
        model.refreshUiLanguage()
        com.kigtts.next.manager.HostPluginRuntime.get(application).resumeReadingCache()
        com.kigtts.next.overlay.NativeOverlayService.sync(this)
    }
    override fun onResume() { super.onResume(); com.kigtts.next.overlay.OverlayHostWindow.resume(this); model.resumeCaptionWindow() }
    override fun onPause() { com.kigtts.next.overlay.OverlayHostWindow.pause(this); super.onPause() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        cameraShutterKeys.dispatch(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        cameraShutterKeys.dispatch(event) || super.onKeyUp(keyCode, event)
    override fun onStop() { cameraShutterKeys.clearPressed(); if (!isChangingConfigurations) model.onBackground(); super.onStop() }
}
