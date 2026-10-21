package com.kigtts.next.media

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kigtts.next.state.FeedbackSettingsStore

/** Only named native effects. No arbitrary patterns, durations, background loops or setting bypass. */
internal class WebHapticGate {
    private var touchAt = Long.MIN_VALUE; private var firedAt = Long.MIN_VALUE
    fun touched(now: Long) { touchAt = now }
    fun accept(type: String, enabled: Boolean, active: Boolean, now: Long): Boolean {
        if (type !in TYPES || !enabled || !active || touchAt == Long.MIN_VALUE || now - touchAt !in 0..1500 || firedAt != Long.MIN_VALUE && now - firedAt < 55) return false
        firedAt = now; return true
    }
    companion object { val TYPES = setOf("click", "confirm", "reject", "selection", "toggle-on", "toggle-off", "long-press", "drag-start", "drag-end") }
}
fun WebView.installKigttsHaptics(active: () -> Boolean = { true }) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
    val gate = WebHapticGate(); val preferences = FeedbackSettingsStore(context)
    setOnTouchListener { _, event -> if (event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) gate.touched(SystemClock.uptimeMillis()); false }
    setOnKeyListener { _, _, event -> if (event.action == android.view.KeyEvent.ACTION_DOWN) gate.touched(SystemClock.uptimeMillis()); false }
    WebViewCompat.addWebMessageListener(this, "KigttsHaptics", setOf(WebContentPolicy.ORIGIN)) { _, message, origin, mainFrame, _ ->
        val type = message.data.orEmpty()
        if (mainFrame && origin.toString() == WebContentPolicy.ORIGIN && gate.accept(type, preferences.load(), active() && isShown && hasWindowFocus(), SystemClock.uptimeMillis())) {
            val effect = when (type) {
                "confirm" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                "reject" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                "selection" -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
                "toggle-on" -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
                "toggle-off" -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
                "long-press", "drag-start" -> HapticFeedbackConstants.LONG_PRESS
                "drag-end" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK
                else -> HapticFeedbackConstants.VIRTUAL_KEY
            }
            performHapticFeedback(effect)
        }
    }
}
