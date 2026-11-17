package com.kigtts.next.ui.cards.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.ui.components.MotionOutlinedButton
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun cameraPermission(active: Boolean): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun check() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = check() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val launch = { requested = true; request.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(active) { if (active && !requested && !granted && context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) launch() }
    return granted to launch
}

@Composable
internal fun CameraPermissionPanel(request: () -> Unit, active: Boolean) {
    val copy = rememberCameraCopy()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(copy.text("camera.permission_detail"))
        Spacer(Modifier.height(16.dp))
        MotionOutlinedButton(feedbackClick(request), enabled = active) { Text(copy.text("camera.allow_camera")) }
        MotionOutlinedButton(feedbackClick {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }, enabled = active) { Text(copy.text("camera.app_settings")) }
    }
}
