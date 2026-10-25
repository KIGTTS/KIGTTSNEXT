package com.kigtts.next.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.kigtts.next.ui.navigation.LocalPageActive
import kotlinx.coroutines.delay

internal object SettingFocus {
    data class Target(val title: String, val token: Long = System.nanoTime())
    var target by mutableStateOf<Target?>(null)
}

@Composable internal fun Modifier.settingFocus(title: String): Modifier {
    val request = remember { BringIntoViewRequester() }
    val opacity = remember { Animatable(0f) }
    val target = SettingFocus.target
    val active = LocalPageActive.current
    val color = MaterialTheme.colorScheme.primary
    LaunchedEffect(target, active, title) {
        opacity.snapTo(0f)
        if (target?.title == title && active) {
            delay(320)
            request.bringIntoView()
            repeat(2) {
                opacity.animateTo(.15f, tween(420, easing = FastOutSlowInEasing))
                opacity.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
            if (SettingFocus.target == target) SettingFocus.target = null
        }
    }
    return bringIntoViewRequester(request).drawWithContent { drawContent(); drawRect(color.copy(alpha = opacity.value)) }
}
