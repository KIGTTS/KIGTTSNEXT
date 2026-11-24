package com.kigtts.next.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.components.ProvideButtonFeedback

@Composable
internal fun FirstRunHost(model: AppViewModel, progress: FirstRunStore, startup: NativeStartup,
    complete: (Boolean) -> Unit, exit: () -> Unit) {
    var step by remember(progress) { mutableStateOf(progress.step()) }
    if (step == FirstRunStep.COMPLETE) {
        KigttsApp(model, progress, startup)
        return
    }
    val state by model.state.collectAsStateWithLifecycle()
    KigttsTheme(state.theme) {
        val colors = MaterialTheme.colorScheme
        SideEffect { startup.ready(colors) }
        CompositionLocalProvider(LocalUiLocale provides state.uiLocale) {
            ProvideButtonFeedback(state.hapticsEnabled) {
                OnboardingScreen(step, state.theme, state.subtitleAppearance.reducedMotion, model::theme,
                    next = { tryQuickText ->
                        step = progress.advance(step)
                        if (step == FirstRunStep.COMPLETE) complete(tryQuickText)
                    }, back = { step = progress.back(step) }, exit = exit)
            }
        }
    }
}
