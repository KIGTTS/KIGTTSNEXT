package com.kigtts.next.ui.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.kigtts.next.media.WebAndroidPermissions

@Composable internal fun WebAndroidPermissionHost() {
    val pending by WebAndroidPermissions.pending.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), WebAndroidPermissions::finish)
    LaunchedEffect(pending) { pending?.let { launcher.launch(it.permissions.toTypedArray()) } }
    DisposableEffect(Unit) { onDispose { WebAndroidPermissions.cancel() } }
}
