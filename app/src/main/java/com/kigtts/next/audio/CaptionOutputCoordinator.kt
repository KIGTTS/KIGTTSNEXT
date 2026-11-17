package com.kigtts.next.audio

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.kigtts.next.MainActivity
import com.kigtts.next.state.AppState
import com.kigtts.next.overlay.OverlayHostWindow
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Native permission/output transport only; the service plugin selects the output policy. */
internal class CaptionOutputCoordinator(private val context: Context, scope: CoroutineScope, private val state: StateFlow<AppState>,
    private val begin: (RecognitionSession) -> Unit, private val stop: () -> Unit, private val message: (String) -> Unit,
    private val serviceId: () -> String?) {
    private val captionFonts = CaptionFontSettings(context)
    private val windowSize = MutableStateFlow(captionFonts.window())
    private val overlay = FloatingCaptions(context, ::toggleFromWindow, ::dismissWindow,
        { size -> windowSize.value = size }, { factor ->
            val range = CaptionFontSettings.WINDOW_RANGE
            windowSize.update { (it * factor).coerceIn(range.start, range.endInclusive) }
        }, { captionFonts.saveWindow(windowSize.value) })
    private val manual = MutableStateFlow(Manual())
    private var pendingPermission: (() -> Unit)? = null
    private var startOnResume = false
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val permissionRequests = requests.receiveAsFlow()
    private val observation = scope.launch {
        var foreground = false
        val output = state.map { Output(it.listening && !it.voiceInputActive, it.recognitionOutput,
            it.transcript, it.theme, it.hapticsEnabled, it.uiLocale) }.distinctUntilChanged()
        combine(output, manual, windowSize, ::Triple).collect { (value, window, fontSize) ->
                if ((window.open || value.active && value.kind != CaptionOutput.PANEL && !window.suppressed) && Settings.canDrawOverlays(context)) {
                    runCatching { overlay.show(value.text.takeIf { value.active }.orEmpty(), fontSize, value.theme, value.active, value.haptics, value.locale) }
                        .onFailure { manual.value = Manual(suppressed = true); message("无法显示字幕浮窗，请检查悬浮窗权限") }
                } else overlay.close()
                if (foreground != value.active) {
                    foreground = value.active
                    if (foreground) runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, RecognitionForegroundService::class.java)) }
                        .onFailure { message("无法保持后台聆听，请保持 KIGTTS 在前台") }
                    else context.stopService(Intent(context, RecognitionForegroundService::class.java))
                }
        }
    }
    fun start(session: RecognitionSession) {
        if (session.purpose == RecognitionPurpose.CAPTIONS && session.output != CaptionOutput.PANEL)
            withOverlayPermission { manual.update { it.copy(suppressed = false) }; begin(session) }
        else begin(session)
    }
    fun openWindow() = withOverlayPermission { manual.value = Manual(open = true) }
    fun windowOpen() = manual.value.open || state.value.let {
        it.listening && !it.voiceInputActive && it.recognitionOutput != CaptionOutput.PANEL && !manual.value.suppressed
    }

    private fun toggleFromWindow() {
        if (state.value.listening && !state.value.voiceInputActive) { stop(); return }
        if (OverlayHostWindow.inForeground) startWindowSession()
        else {
            startOnResume = true
            runCatching { context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                .onFailure { startOnResume = false; message("请先打开 KIGTTS，再开始聆听") }
        }
    }
    fun resume() {
        if (!startOnResume) return
        startOnResume = false
        if (manual.value.open) startWindowSession()
    }
    private fun startWindowSession() {
        val id = serviceId() ?: run { message("请先启用语音识别服务"); return }
        start(RecognitionSession(id, RecognitionPurpose.CAPTIONS, output = CaptionOutput.FLOATING))
    }
    private fun dismissWindow() {
        startOnResume = false
        manual.value = Manual(suppressed = true)
        val current = state.value
        if (current.listening && !current.captionsVisible && !current.voiceInputActive) stop()
    }
    fun resetWindow() { startOnResume = false; pendingPermission = null; manual.value = Manual(suppressed = true) }
    private fun withOverlayPermission(action: () -> Unit) {
        if (Settings.canDrawOverlays(context)) action()
        else { pendingPermission = action; requests.trySend(Unit) }
    }
    fun permissionResult() {
        val next = pendingPermission; pendingPermission = null
        if (next != null) {
            if (Settings.canDrawOverlays(context)) next() else message("未开启字幕浮窗权限")
        }
    }
    fun close() { pendingPermission = null; startOnResume = false; observation.cancel(); overlay.close(); context.stopService(Intent(context, RecognitionForegroundService::class.java)) }
    private data class Manual(val open: Boolean = false, val suppressed: Boolean = false)
    private data class Output(val active: Boolean, val kind: CaptionOutput, val text: String,
        val theme: com.kigtts.next.theme.ThemeSettings, val haptics: Boolean, val locale: String)
}
