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
    var finished by remember(progress) { mutableStateOf(progress.step() == FirstRunStep.COMPLETE) }
    if (finished) {
        KigttsApp(model, progress, startup)
        return
    }
    val state by model.state.collectAsStateWithLifecycle()
    KigttsTheme(state.theme) {
        val colors = MaterialTheme.colorScheme
        SideEffect { startup.ready(colors) }
        CompositionLocalProvider(LocalUiLocale provides state.uiLocale) {
            ProvideButtonFeedback(state.hapticsEnabled) {
                OnboardingScreen(progress, state.theme, state.subtitleAppearance.reducedMotion, model::theme,
                    complete = { tryQuickText ->
                        if (progress.finish()) {
                            finished = true
                            complete(tryQuickText)
                        }
                    }, exit = exit)
            }
        }
    }
}
