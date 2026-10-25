package com.kigtts.next.media

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import com.kigtts.plugin.api.AppCommand
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

/** Computer-style input is confined to an inspection viewport, never the real app controls. */
internal object BrowserInput {
    val operations = setOf("click", "type", "scroll", "drag", "long-press", "hover", "key")

    suspend fun perform(web: WebView, command: AppCommand.BrowserSession, offsetX: Int = 0, offsetY: Int = 0) {
        fun point(x: Float, y: Float): Pair<Float, Float> {
            val local = x - offsetX to y - offsetY
            require(local.first.isFinite() && local.second.isFinite() && local.first >= 0 && local.first < web.width && local.second >= 0 && local.second < web.height) { "坐标在网页取景区域外（${web.width}×${web.height}，${local.first},${local.second}）" }
            return local
        }
        suspend fun target(): Pair<Float, Float> = if (command.selector != null) {
            val result = web.browserJson(BrowserDriverScripts.action("click", command.selector!!, ""))
            check(result.optBoolean("ok")) { result.optString("error") }
            (result.getDouble("x") * web.width).toFloat() to (result.getDouble("y") * web.height).toFloat()
        } else point(requireNotNull(command.x), requireNotNull(command.y))
        when (command.operation) {
            "type" -> {
                val result = web.browserJson(BrowserDriverScripts.action("type", requireNotNull(command.selector), command.text))
                check(result.optBoolean("ok")) { result.optString("error") }
            }
            "click", "long-press" -> {
                val p = target(); touch(web, listOf(p), if (command.operation == "long-press") 650 else 100)
            }
            "drag" -> {
                val data = JSONObject(command.text)
                val values = data.getJSONArray("points"); require(values.length() in 2..64)
                val points = (0 until values.length()).map { index -> values.getJSONArray(index).let { point(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
                touch(web, points, data.optInt("durationMs", 600).also { require(it in 100..5000) })
            }
            "hover" -> {
                val p = target(); val time = android.os.SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_HOVER_MOVE, p.first, p.second, 0)
                try { event.source = InputDevice.SOURCE_MOUSE; web.dispatchGenericMotionEvent(event) } finally { event.recycle() }
            }
            "key" -> {
                val parts = command.text.uppercase(java.util.Locale.ROOT).split('+')
                require(parts.size in 1..4 && parts.dropLast(1).all { it in setOf("CTRL", "ALT", "SHIFT", "META") })
                val key = keyCode(parts.last()); require(key != KeyEvent.KEYCODE_UNKNOWN) { "不支持此按键" }
                val meta = parts.dropLast(1).fold(0) { state, modifier -> state or when (modifier) { "CTRL" -> KeyEvent.META_CTRL_ON; "ALT" -> KeyEvent.META_ALT_ON; "SHIFT" -> KeyEvent.META_SHIFT_ON; else -> KeyEvent.META_META_ON } }
                val time = android.os.SystemClock.uptimeMillis()
                web.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0, meta))
                web.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0, meta))
            }
            "scroll" -> {
                val dx = if (command.text.isBlank()) 0.0 else JSONObject(command.text).optDouble("deltaX", 0.0)
                require(dx.isFinite() && kotlin.math.abs(dx) <= 20000 && command.deltaY.isFinite() && kotlin.math.abs(command.deltaY) <= 20000)
                val selector = JSONObject.quote(command.selector.orEmpty())
                val result = web.browserJson("""(()=>{const q=$selector,e=q?document.querySelector(q):document.scrollingElement;if(!e)return JSON.stringify({ok:false,error:'没有此滚动区域'});e.scrollBy({left:$dx,top:${command.deltaY},behavior:'instant'});return JSON.stringify({ok:true});})()""")
                check(result.optBoolean("ok")) { result.optString("error") }
            }
        }
    }

    private suspend fun touch(web: WebView, points: List<Pair<Float, Float>>, duration: Int) {
        val start = android.os.SystemClock.uptimeMillis()
        var latest = points.first()
        fun send(action: Int, p: Pair<Float, Float>) {
            val event = MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(), action, p.first, p.second, 0)
            try { event.source = InputDevice.SOURCE_TOUCHSCREEN; web.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, latest)
        var completed = false
        try {
            val steps = (duration / 16).coerceAtLeast(1)
            repeat(steps) { index ->
                delay((duration.toLong() / steps).coerceAtLeast(1))
                val position = (index + 1f) / steps * (points.size - 1)
                val a = position.toInt().coerceAtMost(points.lastIndex); val b = (a + 1).coerceAtMost(points.lastIndex); val t = position - a
                latest = points[a].first + (points[b].first - points[a].first) * t to points[a].second + (points[b].second - points[a].second) * t
                if (points.size > 1) send(MotionEvent.ACTION_MOVE, latest)
            }
            completed = true
        } finally { send(if (completed) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL, latest) }
    }

    internal fun keyCode(key: String): Int = when (key) {
        "ENTER", "RETURN" -> KeyEvent.KEYCODE_ENTER; "TAB" -> KeyEvent.KEYCODE_TAB
        "ESC", "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE; "BACKSPACE" -> KeyEvent.KEYCODE_DEL
        "DELETE" -> KeyEvent.KEYCODE_FORWARD_DEL; "SPACE" -> KeyEvent.KEYCODE_SPACE
        "ARROWLEFT" -> KeyEvent.KEYCODE_DPAD_LEFT; "ARROWRIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "ARROWUP" -> KeyEvent.KEYCODE_DPAD_UP; "ARROWDOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
        "HOME" -> KeyEvent.KEYCODE_MOVE_HOME; "END" -> KeyEvent.KEYCODE_MOVE_END
        "PAGEUP" -> KeyEvent.KEYCODE_PAGE_UP; "PAGEDOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
        else -> if (key.length == 1 && key[0] in 'A'..'Z') KeyEvent.KEYCODE_A + (key[0] - 'A') else KeyEvent.KEYCODE_UNKNOWN
    }
}
