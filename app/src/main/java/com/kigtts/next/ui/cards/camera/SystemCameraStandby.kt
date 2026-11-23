package com.kigtts.next.ui.cards.camera

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.navigation.LocalPageSettled

@Composable
internal fun SystemCameraStandby(model: AppViewModel, destination: Destination.CardCamera) {
    val context = LocalContext.current
    val settled = LocalPageSettled.current
    var launched by rememberSaveable(destination.requestId) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (model.state.value.destination == destination) model.navigateBack()
    }
    LaunchedEffect(settled, destination) {
        if (!settled || launched) return@LaunchedEffect
        withFrameNanos { }
        if (model.state.value.destination != destination) return@LaunchedEffect
        launched = true
        val opened = try {
            launcher.launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (!opened) {
            model.message(context.uiCopy("camera.system_missing"))
            model.navigateBack()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF17191C)).testTag("system-camera-standby"),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PhotoCamera, uiText("nav.camera"), Modifier.size(56.dp), tint = Color(0xFFB9BFC6))
    }
}
