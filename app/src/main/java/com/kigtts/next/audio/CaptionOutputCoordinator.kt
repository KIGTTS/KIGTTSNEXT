package com.kigtts.next.audio

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.kigtts.next.state.AppState
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Native permission/output transport only; the service plugin selects the output policy. */
internal class CaptionOutputCoordinator(private val context: Context, scope: CoroutineScope, state: StateFlow<AppState>,
    private val begin: (RecognitionSession) -> Unit, stop: () -> Unit, private val message: (String) -> Unit) {
    private val overlay = FloatingCaptions(context, stop)
    private var pending: RecognitionSession? = null
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val permissionRequests = requests.receiveAsFlow()
    private val observation = scope.launch {
        var foreground = false
        state.map { Output(it.listening && !it.voiceInputActive, it.recognitionOutput, it.transcript, it.captionTextSize, it.theme.cornerRadius) }
            .distinctUntilChanged().collect { output ->
                if (output.active && output.kind != CaptionOutput.PANEL && Settings.canDrawOverlays(context)) {
                    runCatching { overlay.show(output.text, output.textSize, output.radius) }.onFailure { message("无法显示悬浮字幕，请检查悬浮窗权限") }
                } else overlay.close()
                if (foreground != output.active) {
                    foreground = output.active
                    if (foreground) runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, RecognitionForegroundService::class.java)) }
                        .onFailure { message("无法保持后台聆听，请保持 KIGTTS 在前台") }
                    else context.stopService(Intent(context, RecognitionForegroundService::class.java))
                }
            }
    }
    fun start(session: RecognitionSession) {
        if (session.purpose == RecognitionPurpose.CAPTIONS && session.output != CaptionOutput.PANEL && !Settings.canDrawOverlays(context)) {
            pending = session; requests.trySend(Unit)
        } else begin(session)
    }
    fun permissionResult() {
        val next = pending; pending = null
        if (Settings.canDrawOverlays(context)) next?.let(begin) else message("未开启悬浮窗权限，可在语音识别设置中改为面板显示")
    }
    fun close() { pending = null; observation.cancel(); overlay.close(); context.stopService(Intent(context, RecognitionForegroundService::class.java)) }
    private data class Output(val active: Boolean, val kind: CaptionOutput, val text: String, val textSize: Float, val radius: Float)
}
