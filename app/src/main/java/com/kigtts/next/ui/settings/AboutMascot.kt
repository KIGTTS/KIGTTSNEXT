package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.onboarding.OnboardingWelcomeArt
import com.kigtts.next.ui.onboarding.WELCOME_ART_ASPECT
import com.kigtts.next.ui.onboarding.rememberOnboardingTilt

@Composable
internal fun AboutMascot(reducedMotion: Boolean) {
    var visible by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    val active = visible && LocalPageActive.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(WELCOME_ART_ASPECT)
            .testTag("about-mascot").onGloballyPositioned {
                visible = it.boundsInWindow().height >= it.size.height * .2f && it.size.height > 0
                if (visible) appeared = true
            }) {
            if (appeared) {
                val (_, tilt) = rememberOnboardingTilt(active && !reducedMotion)
                OnboardingWelcomeArt(Modifier.matchParentSize(), active, reducedMotion, tilt)
            }
        }
    }
}
