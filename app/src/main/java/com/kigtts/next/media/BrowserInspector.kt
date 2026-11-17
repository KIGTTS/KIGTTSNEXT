package com.kigtts.next.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.*
import java.io.File

data class BrowserCaptureResult(val width: Int, val height: Int, val diagnostics: List<String>)

/** A real WebView attached only to the private browser surface. */
object BrowserInspector {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    suspend fun capture(context: Context, document: BrowserDocument, destination: File, width: Int, height: Int): BrowserCaptureResult {
        require(width in 240..2048 && height in 240..2048 && width.toLong() * height <= 3_000_000) { "截图尺寸需为240至2048且不超过300万像素" }
        mutex.lock()
        try { return withContext(Dispatchers.Main) {
            val surface = BrowserInspectionSurface.acquire(context)
            val parent = surface.root
            val ready = CompletableDeferred<Unit>(); val diagnostics = mutableListOf<String>()
            var created: android.webkit.WebView? = null
            try {
                val web = createBrowserView(parent.context, document.copy(actions = emptyList()),
                    ready = { ready.complete(Unit) }, error = { if (diagnostics.size < 20) diagnostics += it.take(1000) })
                created = web
                BrowserSimulation.prepare(web)
                web.isFocusable = false; web.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                parent.addView(web, 0, android.view.ViewGroup.LayoutParams(width, height))
                web.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
                web.layout(0, 0, width, height); web.loadUrl(document.url)
                withTimeout(35_000) {
                    ready.await(); web.applyKigttsTheme()
                    BrowserSimulation.calibrate(web)
                    // Fonts and page scripts receive a rendering opportunity before the compositor fence.
                    diagnostics += web.awaitBrowserReady()
                    web.awaitBrowserFrame()
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
            } finally {
                try { created?.let { parent.removeView(it); it.stopLoading(); it.destroy() } }
                finally { surface.close() }
            }
        } } finally { mutex.unlock() }
    }
}
