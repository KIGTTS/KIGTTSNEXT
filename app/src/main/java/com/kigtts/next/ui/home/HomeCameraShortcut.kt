package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiCopy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Open the camera app directly; this shortcut does not request a photo back. */
@Composable
internal fun systemCameraShortcut(message: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        try {
            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (_: ActivityNotFoundException) {
            message(context.uiCopy("camera.system_missing"))
        } catch (_: SecurityException) {
            message(context.uiCopy("camera.system_missing"))
        }
    }
}
