package com.kigtts.next.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.*
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

data class BrowserCaptureResult(val width: Int, val height: Int, val diagnostics: List<String>)

/** A real, attached WebView behind the app content; no screenshot of unrelated apps or host UI. */
object BrowserInspector {
    private var root = WeakReference<ViewGroup>(null)
    private val mutex = kotlinx.coroutines.sync.Mutex()
    fun register(view: ViewGroup) { root = WeakReference(view) }
    fun unregister(view: ViewGroup) { if (root.get() === view) { BrowserSessions.closeAll(); root.clear() } }
    internal fun attachedRoot() = root.get()?.takeIf { it.isAttachedToWindow }
    suspend fun capture(context: Context, document: BrowserDocument, destination: File, width: Int, height: Int): BrowserCaptureResult {
        require(width in 240..2048 && height in 240..2048 && width.toLong() * height <= 3_000_000) { "截图尺寸需为240至2048且不超过300万像素" }
        mutex.lock()
        try { return withContext(Dispatchers.Main) {
            val parent = root.get()?.takeIf { it.isAttachedToWindow } ?: error("请保持KIGTTS在前台以进行网页截图验收")
            val ready = CompletableDeferred<Unit>(); val diagnostics = mutableListOf<String>()
            val web = createBrowserView(context, document, ready = { ready.complete(Unit) }, error = { if (diagnostics.size < 20) diagnostics += it.take(1000) })
            BrowserSimulation.prepare(web)
            web.alpha = 0f; web.isFocusable = false; web.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            parent.addView(web, 0, ViewGroup.LayoutParams(width, height))
            try {
                web.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
                web.layout(0, 0, width, height); web.loadUrl(document.url)
                withTimeout(15_000) {
                    ready.await(); web.applyKigttsTheme()
                    BrowserSimulation.calibrate(web)
                    // Fonts and page scripts receive a rendering opportunity before the compositor fence.
                    diagnostics += web.awaitBrowserReady()
                    suspendCancellableCoroutine { continuation ->
                        web.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) { if (continuation.isActive) continuation.resume(Unit) }
                        })
                    }
                }
                // Bitmap's global default density may differ from this window's user-selected
                // display density. A mismatched Canvas density scales WebView content twice.
                val bitmap = Bitmap.createBitmap(web.resources.displayMetrics, width, height, Bitmap.Config.ARGB_8888)
                try {
                    diagnostics += BrowserCanvasCapture.prepare(web)
                    web.draw(Canvas(bitmap))
                    withContext(Dispatchers.IO) { destination.parentFile!!.mkdirs(); destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                } finally { bitmap.recycle(); BrowserCanvasCapture.restore(web) }
                BrowserCaptureResult(width, height, diagnostics.toList())
            } finally { parent.removeView(web); web.stopLoading(); web.destroy() }
        } } finally { mutex.unlock() }
    }
}
