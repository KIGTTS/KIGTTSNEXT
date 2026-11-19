package com.kigtts.next.audio

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.state.AppState
import com.kigtts.next.overlay.OverlayHostWindow
import com.kigtts.next.ui.home.CaptionVisuals
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Native permission/output transport only; the service plugin selects the output policy. */
internal class CaptionOutputCoordinator(private val context: Context, scope: CoroutineScope, private val state: StateFlow<AppState>,
    private val begin: (RecognitionSession) -> Unit, private val stop: () -> Unit, private val message: (String) -> Unit,
    private val serviceId: () -> String?, private val microphone: RecognitionForegroundService.Microphone?) {
    private val captionFonts = CaptionFontSettings(context)
    private val windowSize = MutableStateFlow(captionFonts.window())
    private val overlay = FloatingCaptions(context, ::toggleFromWindow, ::dismissWindow,
        { size -> windowSize.value = size }, { factor ->
            val range = CaptionFontSettings.WINDOW_RANGE
            windowSize.update { (it * factor).coerceIn(range.start, range.endInclusive) }
        }, { captionFonts.saveWindow(windowSize.value) })
    private val manual = MutableStateFlow(Manual())
    private val confirmation = CaptionStartNotification(context, ::copy) {
        if (manual.value.open && windowOpen()) startWindowSession()
    }
    private var pendingPermission: (() -> Unit)? = null
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val permissionRequests = requests.receiveAsFlow()
    private val observation = scope.launch {
        val output = state.map { Output(it.listening && !it.voiceInputActive, it.recognitionOutput,
            it.transcript, it.theme, it.hapticsEnabled, it.uiLocale,
            CaptionVisuals(it.recognitionFinalOnly, it.subtitleAppearance.reducedMotion, it.subtitleAppearance.displayReflow)) }.distinctUntilChanged()
        combine(output, manual, windowSize, ::Triple).collect { (value, window, fontSize) ->
            if ((window.open || value.active && value.kind != CaptionOutput.PANEL && !window.suppressed) && Settings.canDrawOverlays(context)) {
                runCatching { overlay.show(value.text.takeIf { value.active }.orEmpty(), fontSize, value.theme,
                    value.active, value.haptics, value.locale, value.visuals) }
                    .onFailure { dismissWindow(); message(copy("listening.window_unavailable")) }
            } else overlay.close()
        }
    }
    fun start(session: RecognitionSession) {
        if (session.purpose == RecognitionPurpose.CAPTIONS && session.output != CaptionOutput.PANEL)
            withOverlayPermission { manual.update { it.copy(suppressed = false) }; begin(session) }
        else begin(session)
    }
    fun openWindow() = withOverlayPermission {
        microphone?.window(open = true, hostVisible = OverlayHostWindow.inForeground)
        manual.value = Manual(open = true)
    }
    fun windowOpen() = (manual.value.open || state.value.let {
        it.listening && !it.voiceInputActive && it.recognitionOutput != CaptionOutput.PANEL && !manual.value.suppressed
    }) && Settings.canDrawOverlays(context)

    private fun toggleFromWindow() {
        if (state.value.listening && !state.value.voiceInputActive) { stop(); return }
        if (OverlayHostWindow.inForeground || microphone?.let { it.prepared && it.permitted } == true) startWindowSession()
        else Toast.makeText(context, copy(if (microphone?.permitted == true && confirmation.show())
            "listening.start_from_notification" else "listening.open_first"), Toast.LENGTH_SHORT).show()
    }
    fun resume() {
        if (!manual.value.open) return
        confirmation.clear()
        microphone?.window(open = true, hostVisible = true)
    }
    private fun startWindowSession() {
        confirmation.clear()
        val id = serviceId() ?: run { message(copy("listening.service_disabled")); return }
        start(RecognitionSession(id, RecognitionPurpose.CAPTIONS, output = CaptionOutput.FLOATING))
    }
    private fun dismissWindow() {
        confirmation.clear()
        microphone?.window(open = false, hostVisible = false)
        manual.value = Manual(suppressed = true)
        val current = state.value
        val hasActivePanel = current.listening && current.captionsVisible &&
            current.recognitionOutput != CaptionOutput.FLOATING && OverlayHostWindow.inForeground
        if (!hasActivePanel) stop()
    }
    fun resetWindow() {
        confirmation.clear()
        microphone?.window(open = false, hostVisible = false)
        pendingPermission = null
        manual.value = Manual(suppressed = true)
    }
    private fun withOverlayPermission(action: () -> Unit) {
        if (Settings.canDrawOverlays(context)) action()
        else { pendingPermission = action; requests.trySend(Unit) }
    }
    fun permissionResult() {
        val next = pendingPermission; pendingPermission = null
        if (next != null) {
            if (Settings.canDrawOverlays(context)) next() else message(copy("listening.window_permission"))
        }
    }
    fun close() { observation.cancel(); resetWindow(); overlay.close() }
    private fun copy(key: String) = context.uiCopy(key, locale = state.value.uiLocale)

    private data class Manual(val open: Boolean = false, val suppressed: Boolean = false)
    private data class Output(val active: Boolean, val kind: CaptionOutput, val text: String,
        val theme: com.kigtts.next.theme.ThemeSettings, val haptics: Boolean, val locale: String, val visuals: CaptionVisuals)
}
