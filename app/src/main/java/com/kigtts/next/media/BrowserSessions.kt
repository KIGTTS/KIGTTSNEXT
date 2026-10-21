package com.kigtts.next.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import com.kigtts.plugin.api.AppCommand
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Isolated inspection state persists between tools; no native actions, haptics, sensors, microphone or camera. */
internal object BrowserSessions {
    private data class Session(val owner: String, val web: WebView, val width: Int, val height: Int, val diagnostics: MutableList<String>,
        val subtitle: BrowserSubtitleLayout? = null, var touched: Long = android.os.SystemClock.elapsedRealtime())
    private val sessions = linkedMapOf<String, Session>()
    private val gate = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cleanup: Job? = null
    private fun expire() { val now = android.os.SystemClock.elapsedRealtime(); sessions.filterValues { now - it.touched > 10 * 60 * 1000 }.keys.toList().forEach { sessions.remove(it)?.let(::release) } }
    private fun scheduleCleanup() { if (cleanup?.isActive != true) cleanup = scope.launch { while (sessions.isNotEmpty()) { delay(60000); expire() } } }
    fun closeAll() { cleanup?.cancel(); cleanup = null; sessions.values.toList().forEach { release(it) }; sessions.clear() }
    private fun release(session: Session) { val view = session.subtitle ?: session.web; (view.parent as? ViewGroup)?.removeView(view); session.web.stopLoading(); session.web.destroy() }
    private suspend fun state(session: Session): JSONObject {
        val result = session.web.browserJson(BrowserDriverScripts.state)
        // innerWidth can include horizontal overflow and is not the visible CSS viewport.
        val cssWidth = result.optDouble("width", session.width.toDouble()).coerceAtLeast(1.0)
        val scale = session.web.width.toDouble() / cssWidth
        val elements = result.optJSONArray("elements")
        if (elements != null) for (index in 0 until elements.length()) {
            val element = elements.getJSONObject(index)
            for (key in listOf("x", "y", "width", "height")) element.put(key, element.optDouble(key) * scale + when (key) { "x" -> session.subtitle?.viewport?.left ?: 0; "y" -> session.subtitle?.viewport?.top ?: 0; else -> 0 })
        }
        return result.put("cssWidth", cssWidth).put("cssHeight", result.optDouble("height"))
            .put("width", session.width).put("height", session.height).put("coordinateSpace", "screenshot-pixels")
            .put("layout", if (session.subtitle != null) "subtitle-simulation" else "webview-viewport")
            .also { result -> session.subtitle?.let { result.put("viewport", it.geometryJson()) } }
    }
    suspend fun run(owner: String, command: AppCommand.BrowserSession, document: BrowserDocument?, output: File?): JSONObject = gate.withLock {
        withContext(Dispatchers.Main) {
            expire()
            require(command.operation in setOf("open", "open-subtitle", "state", "click", "type", "scroll", "wait", "screenshot", "close"))
            require(command.text.length <= 16000 && (command.selector?.length ?: 0) <= 500)
            if (command.operation in setOf("open", "open-subtitle")) {
                require(command.width in 240..2048 && command.height in 240..2048 && command.width.toLong() * command.height <= 3_000_000)
                check(sessions.values.count { it.owner == owner } < 3 && sessions.size < 6) { "浏览器会话过多，请先关闭不用的会话" }
                val parent = BrowserInspector.attachedRoot() ?: error("请保持KIGTTS在前台以建立独立浏览器")
                val ready = CompletableDeferred<Unit>(); val diagnostics = mutableListOf<String>()
                val web = createBrowserView(parent.context, requireNotNull(document).copy(actions = emptyList()),
                    ready = { ready.complete(Unit) }, error = { if (diagnostics.size < 20) diagnostics += it.take(600) })
                val subtitle = if (command.operation == "open-subtitle") BrowserSubtitleLayout(parent.context, command.width, command.height, web) else null
                val view = subtitle ?: web
                view.alpha = 0f; web.isFocusable = false; web.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                val session = Session(owner, web, command.width, command.height, diagnostics, subtitle)
                try {
                    parent.addView(view, 0, ViewGroup.LayoutParams(command.width, command.height))
                    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(command.width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(command.height, android.view.View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, command.width, command.height); web.loadUrl(document.url)
                    withTimeout(command.timeoutMs.toLong().coerceIn(500, 30000)) { ready.await() }
                    subtitle?.applySafeArea()
                    diagnostics += web.awaitBrowserReady(command.waitMs, command.timeoutMs, command.selector)
                    val id = UUID.randomUUID().toString(); sessions[id] = session; scheduleCleanup()
                    state(session).put("ok", true).put("sessionId", id).put("diagnostics", org.json.JSONArray(diagnostics))
                } catch (failure: Throwable) { release(session); throw failure }
            } else {
                val id = requireNotNull(command.sessionId)
                val session = sessions[id]?.takeIf { it.owner == owner } ?: error("SESSION_EXPIRED：请重新打开浏览器会话")
                session.touched = android.os.SystemClock.elapsedRealtime()
                val web = session.web
                check(web.isAttachedToWindow) { "SESSION_EXPIRED：主界面已重建" }
                when (command.operation) {
                    "close" -> { sessions.remove(id); release(session); JSONObject().put("ok", true).put("closed", true) }
                    else -> {
                        when (command.operation) {
                            "click" -> if (command.selector != null) { val result = web.browserJson(BrowserDriverScripts.action("click", requireNotNull(command.selector), "")); check(result.optBoolean("ok")) { result.optString("error") } }
                            else {
                                val x = requireNotNull(command.x); val y = requireNotNull(command.y)
                                val localX = x - (session.subtitle?.viewport?.left ?: 0); val localY = y - (session.subtitle?.viewport?.top ?: 0)
                                require(localX.isFinite() && localY.isFinite() && localX >= 0 && localX < web.width && localY >= 0 && localY < web.height) { "坐标在网页取景区域外，不能操作模拟宿主控件" }
                                val time = android.os.SystemClock.uptimeMillis()
                                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) { val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 50 else 0, action, localX, localY, 0); try { web.dispatchTouchEvent(event) } finally { event.recycle() } }
                            }
                            "type" -> { val result = web.browserJson(BrowserDriverScripts.action("type", requireNotNull(command.selector), command.text)); check(result.optBoolean("ok")) { result.optString("error") } }
                            "scroll" -> { require(command.deltaY.isFinite() && kotlin.math.abs(command.deltaY) <= 20000); web.browserJson("(()=>{scrollBy(0,${command.deltaY});return '{}';})()") }
                        }
                        if (command.operation != "state") session.diagnostics += web.awaitBrowserReady(command.waitMs, command.timeoutMs, command.selector.takeIf { command.operation == "wait" })
                        val result = state(session).put("ok", true).put("sessionId", id)
                        if (command.operation == "screenshot") {
                            web.awaitBrowserFrame()
                            val bitmap = Bitmap.createBitmap(web.resources.displayMetrics, session.width, session.height, Bitmap.Config.ARGB_8888)
                            try { (session.subtitle ?: web).draw(Canvas(bitmap)); withContext(Dispatchers.IO) { requireNotNull(output).parentFile!!.mkdirs(); output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } }
                            finally { bitmap.recycle() }
                            result.put("width", session.width).put("height", session.height)
                        }
                        result.put("diagnostics", org.json.JSONArray(session.diagnostics.takeLast(20)))
                    }
                }
            }
        }
    }
}
