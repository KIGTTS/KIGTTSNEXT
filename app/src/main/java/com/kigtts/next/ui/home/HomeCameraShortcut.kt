package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiCopy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.state.AppViewModel

/** Resolve the current preference on each gesture, including after returning from Settings. */
@Composable
internal fun homeCameraShortcut(model: AppViewModel): (longPress: Boolean) -> Unit {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("polaroid_camera", 0) }
    return remember(context, model, preferences) {
        { longPress ->
            val preferSystem = preferences.getBoolean("use-system-camera", false)
            if (preferSystem == longPress) {
                model.cardCapture.open(systemCamera = false)
            } else {
                try {
                    context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                } catch (_: ActivityNotFoundException) {
                    model.message(context.uiCopy("camera.system_missing"))
                } catch (_: SecurityException) {
                    model.message(context.uiCopy("camera.system_missing"))
                }
            }
        }
    }
}
