package com.kigtts.next.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    var context = LocalContext.current
    while (context is ContextWrapper && context !is Activity) context = context.baseContext
    val activity = context as? Activity
    SideEffect {
        if (activity != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
