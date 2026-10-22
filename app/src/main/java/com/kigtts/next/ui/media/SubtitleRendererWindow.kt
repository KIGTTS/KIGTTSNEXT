package com.kigtts.next.ui.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.json.JSONObject

internal data class RendererWindowSettings(val keepScreenOn: Boolean = false,
    val followSystemBrightness: Boolean = true, val brightness: Float = 1f, val quickSwipe: Boolean = false) {
    companion object {
        fun parse(value: String): RendererWindowSettings {
            val json = runCatching { JSONObject(value) }.getOrElse { JSONObject() }
            return RendererWindowSettings(json.optBoolean("keepScreenOn", false),
                json.optBoolean("followSystemBrightness", true),
                json.optDouble("screenBrightness", 1.0).toFloat().takeIf(Float::isFinite)?.coerceIn(.1f, 1f) ?: 1f,
                json.optBoolean("quickSwipeOpensQuickText", false))
        }
    }
}

/** A renderer owns only its visible window interval; leaving restores previous host preferences. */
@Composable
internal fun SubtitleRendererWindow(settings: RendererWindowSettings, active: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val window = remember(context) { context.rendererActivity()?.window }
    DisposableEffect(view, window, active, settings) {
        val previousKeep = view.keepScreenOn
        val previousBrightness = window?.attributes?.screenBrightness
        if (active) {
            view.keepScreenOn = settings.keepScreenOn
            window?.let { target -> target.attributes = target.attributes.apply {
                screenBrightness = if (settings.followSystemBrightness) -1f else settings.brightness
            } }
        }
        onDispose {
            if (active) {
                view.keepScreenOn = previousKeep
                if (previousBrightness != null) window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            }
        }
    }
}

private fun Context.rendererActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.rendererActivity() else null
    else -> null
}
