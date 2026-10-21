package com.kigtts.next.media

import android.webkit.WebView
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

internal suspend fun WebView.browserJson(script: String): JSONObject = withTimeout(5000) {
    suspendCancellableCoroutine { continuation -> evaluateJavascript(script) { raw ->
        if (continuation.isActive) continuation.resume(runCatching {
            val value = JSONTokener(raw).nextValue()
            if (value is String) JSONObject(value) else value as? JSONObject ?: JSONObject()
        }.getOrDefault(JSONObject()))
    } }
}

internal suspend fun WebView.awaitBrowserFrame() = withTimeout(5000) {
    suspendCancellableCoroutine<Unit> { continuation ->
        postVisualStateCallback(1, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { if (continuation.isActive) continuation.resume(Unit) }
        })
    }
}

/** Fonts, images, a real viewport and a quiet DOM all matter; onPageFinished alone is insufficient. */
internal suspend fun WebView.awaitBrowserReady(waitMs: Int = 800, timeoutMs: Int = 15000, selector: String? = null): List<String> {
    require(waitMs in 0..10000 && timeoutMs in 500..30000 && (selector?.length ?: 0) <= 500)
    val start = android.os.SystemClock.elapsedRealtime()
    browserJson("""(()=>{if(!window.__kigttsInspection){const s={last:performance.now()};new MutationObserver(()=>s.last=performance.now()).observe(document,{subtree:true,childList:true,attributes:true,characterData:true});window.__kigttsInspection=s;}return '{}';})()""")
    var last = JSONObject()
    while (android.os.SystemClock.elapsedRealtime() - start < timeoutMs) {
        currentCoroutineContext().ensureActive()
        last = browserJson("""(()=>{let target=true;try{const s=${JSONObject.quote(selector.orEmpty())};if(s){const e=document.querySelector(s);target=!!(e&&e.getBoundingClientRect().width&&e.getBoundingClientRect().height);}}catch(e){target=false;}return JSON.stringify({complete:document.readyState==='complete',images:Array.from(document.images).every(i=>i.complete),fonts:!document.fonts||document.fonts.status==='loaded',explicit:window.KigttsPageReady!==false,quiet:performance.now()-(window.__kigttsInspection?.last||0),target});})()""")
        if (android.os.SystemClock.elapsedRealtime() - start >= waitMs && last.optBoolean("complete") && last.optBoolean("images") && last.optBoolean("fonts") && last.optBoolean("explicit") && last.optBoolean("target") && last.optDouble("quiet") >= 300) return emptyList()
        delay(100)
    }
    check(last.optBoolean("complete") && last.optBoolean("explicit") && last.optBoolean("target")) { "页面尚未就绪；请等待运行界面或指定可见元素后重新截图" }
    return listOf("页面仍在持续更新或资源尚未完全稳定，请检查截图并按需继续等待")
}
