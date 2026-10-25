package com.kigtts.next.ui.identity

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.nfc.*
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.identity.*
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun ContactNfcControl(content: String) {
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    if (activity == null || adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) return
    var enabled by remember(content) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, content, lifecycle) {
        val emulation = CardEmulation.getInstance(adapter)
        var token: String? = null
        fun stop() { token?.let(ContactNfcSession::end); token = null; runCatching { emulation.unsetPreferredService(activity) } }
        fun start() {
            if (!enabled || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            try {
                val uri = android.net.Uri.parse(content)
                val record = if (uri.scheme in setOf("https", "http", "mailto", "tel")) NdefRecord.createUri(uri) else NdefRecord.createTextRecord("zh", content)
                val bytes = NdefMessage(arrayOf(record)).toByteArray(); require(bytes.size <= 8190)
                check(adapter.isEnabled && emulation.setPreferredService(activity, ComponentName(context, ContactNfcService::class.java)))
                token = ContactNfcSession.begin(bytes); error = null
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
    TextButton(feedbackClick {
        if (!adapter.isEnabled) runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            .onFailure { error = "请在系统设置中开启 NFC" }
        else enabled = !enabled
    }) { Text(if (enabled) "关闭 NFC 分享" else "开启 NFC 分享") }
    if (enabled) Text("保持此页打开，将两台手机的 NFC 区域靠近。", style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
