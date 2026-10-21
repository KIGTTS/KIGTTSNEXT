package com.kigtts.next.media

import android.content.Context
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Inspection sizes are logical viewport pixels, independent of the phone's physical density. */
internal object BrowserSimulation {
    fun devices(context: Context): JSONObject {
        val metrics = context.resources.displayMetrics
        val density = metrics.density.coerceAtLeast(.5f)
        val width = (metrics.widthPixels / density).roundToInt()
        val height = (metrics.heightPixels / density).roundToInt()
        fun device(id: String, w: Int, h: Int) = JSONObject().put("id", id).put("width", w).put("height", h)
            .put("portrait", JSONObject().put("width", minOf(w, h)).put("height", maxOf(w, h)))
            .put("landscape", JSONObject().put("width", maxOf(w, h)).put("height", minOf(w, h)))
        return JSONObject().put("ok", true).put("sizeUnit", "css-viewport-pixels")
            .put("nativeWidth", metrics.widthPixels).put("nativeHeight", metrics.heightPixels).put("nativeDensity", density)
            .put("devices", JSONArray().put(device("current", width, height)).put(device("phone-small", 360, 800))
                .put(device("phone", 412, 915)).put(device("tablet", 800, 1280)).put(device("desktop", 1280, 800)))
    }
    fun prepare(web: WebView) {
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = false
        BrowserCanvasCapture.install(web)
    }
    suspend fun calibrate(web: WebView) {
        // A normal WebView on a 3.6x device turns 360 native pixels into 100 CSS pixels.
        // Only inspection views use an inverse page scale, so one CSS pixel maps to one output pixel.
        web.browserJson("""(()=>{let m=document.querySelector('meta[name="viewport"]');
          if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
          const s=1/(window.devicePixelRatio||1);
          m.content='width=__WIDTH__,initial-scale='+s+',minimum-scale='+s+',maximum-scale='+s+',user-scalable=no,viewport-fit=cover';
          window.dispatchEvent(new Event('resize'));return '{}';})()""".replace("__WIDTH__", web.width.toString()))
        web.awaitBrowserFrame()
    }
}
