@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.identity.ContactNfcService
import com.kigtts.next.identity.ContactNfcSession

/** One foreground NDEF session, revoked on pause, page exit or payload replacement. */
@Composable internal fun NfcShareControl(payload: ByteArray?, compact: Boolean = false, active: Boolean = true) {
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    if (activity == null || adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) return
    var enabled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, payload, lifecycle, active) {
        val emulation = CardEmulation.getInstance(adapter)
        var token: String? = null
        fun stop() { token?.let(ContactNfcSession::end); token = null; runCatching { emulation.unsetPreferredService(activity) } }
        fun start() {
            if (!enabled || !active || payload == null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            try {
                require(payload.size in 1..8190)
                check(adapter.isEnabled && emulation.setPreferredService(activity, ComponentName(context, ContactNfcService::class.java)))
                token = ContactNfcSession.begin(payload); error = null
            } catch (_: Exception) { stop(); enabled = false; error = "NFC 分享未能开启，请检查系统 NFC 设置" }
        }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> start()
            Lifecycle.Event.ON_PAUSE -> stop()
            else -> Unit
        } }
        lifecycle.addObserver(observer); start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    val toggle = {
        if (!adapter.isEnabled) runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            .onFailure { error = "请在系统设置中开启 NFC" }
        else enabled = !enabled
        Unit
    }
    val label = if (enabled) "关闭 NFC 分享" else "开启 NFC 分享"
    if (compact) ToolButton(label, Icons.Default.Nfc, toggle, size = 40.dp, iconSize = 22.dp, enabled = payload != null,
        colors = if (enabled) compactTonalIconColors() else compactPlainIconColors())
    else {
        TextButton(feedbackClick(toggle), enabled = payload != null) { Text(label) }
        if (enabled) Text("保持此页打开，将两台手机的 NFC 区域靠近。", style = MaterialTheme.typography.bodySmall)
    }
    if (error != null) MotionAlertDialog({ error = null }, title = { Text("NFC 分享") },
        text = { Text(error.orEmpty()) }, confirmButton = { TextButton(dialogFeedbackClick { error = null }) { Text("知道了") } })
}
