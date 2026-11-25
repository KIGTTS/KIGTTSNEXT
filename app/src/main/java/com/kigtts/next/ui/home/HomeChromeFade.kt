package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kigtts.next.state.AppState
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.HideStatusBar
import com.kigtts.next.ui.components.LocalModalWindows
import com.kigtts.next.ui.components.panelIdleWake
import com.kigtts.next.ui.components.rememberPanelIdleChrome
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.HomeChromePresentation

internal data class HomeChromeFade(val wake: Modifier, val header: Modifier, val dock: Modifier)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun homeDrawingInsets(): WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.statusBarsIgnoringVisibility)

@Composable
internal fun rememberHomeChromeFade(state: AppState, model: AppViewModel, renderer: HomeChromePresentation?): HomeChromeFade {
    val requests by model.homeChrome.collectAsState()
    val header = renderer?.fadeHeaderWhenIdle == true || requests.values.any { it.fadeHeaderWhenIdle }
    val dock = renderer?.fadeDockWhenIdle == true || requests.values.any { it.fadeDockWhenIdle }
    val enabled = (header || dock) && LocalPageActive.current && state.sidePage == null
    val keepVisible = state.panel != null || state.quickTextEditing || state.editing || state.mediaEditing ||
        state.voiceInputActive || (LocalModalWindows.current?.count ?: 0) > 0
    val (idle, alpha) = rememberPanelIdleChrome(enabled, keepVisible, state.subtitleAppearance.reducedMotion)
    HideStatusBar(header && enabled && !keepVisible && idle.idle)
    return HomeChromeFade(
        if (enabled) Modifier.panelIdleWake(idle) else Modifier,
        if (header) Modifier.graphicsLayer { this.alpha = alpha.value } else Modifier,
        if (dock) Modifier.graphicsLayer { this.alpha = alpha.value } else Modifier,
    )
}
