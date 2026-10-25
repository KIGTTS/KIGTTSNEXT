package com.kigtts.next.media

import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Theme tokens only; no user text, cookies or host settings are exposed to page scripts. */
data class WebTheme(val dark: Boolean = false, val colors: Map<String, String> = mapOf("primary" to "#00696e", "on-primary" to "#ffffff", "surface" to "#f4fafb", "on-surface" to "#171d1e"), val corner: Int = 24) {
    fun json() = JSONObject().put("dark", dark).put("colors", JSONObject(colors)).put("corner", corner)
    fun css() = ":root{color-scheme:${if (dark) "dark" else "light"};" + colors.entries.joinToString("") { "--md-sys-color-${it.key}:${it.value};" } +
        "--kigtts-primary:var(--md-sys-color-primary);--kigtts-surface:var(--md-sys-color-surface);--kigtts-on-surface:var(--md-sys-color-on-surface);--kigtts-corner:${corner}px;}"
    fun script() = """(()=>{const t=${json()};Object.freeze(t.colors);window.KigttsTheme=Object.freeze(t);
      const apply=()=>{const r=document.documentElement;if(!r)return false;
        r.dataset.kigttsTheme=t.dark?'dark':'light';r.style.colorScheme=t.dark?'dark':'light';
        for(const [role,value] of Object.entries(t.colors))r.style.setProperty('--md-sys-color-'+role,value);
        let s=document.getElementById('kigtts-theme');if(!s){s=document.createElement('style');s.id='kigtts-theme';r.appendChild(s)}
        s.textContent=${JSONObject.quote(css())};return true;};
      if(!apply()){const o=new MutationObserver(()=>{if(apply())o.disconnect()});o.observe(document,{childList:true,subtree:true});}
      window.dispatchEvent(new CustomEvent('kigtts-theme-change',{detail:t}));})()""".trimIndent()
}
object WebThemeState {
    private val mutable = MutableStateFlow(WebTheme())
    val themes = mutable.asStateFlow()
    var current: WebTheme
        get() = mutable.value
        set(value) { mutable.value = value }
}
object WebUiResources {
    fun response(url: String, open: (String) -> java.io.InputStream): WebResourceResponse? {
        if (!url.startsWith(WebContentPolicy.ORIGIN + "/kigtts/")) return null
        val path = url.substringAfter("/kigtts/").substringBefore('?')
        return when (path) {
            "theme.css" -> WebResourceResponse("text/css", "UTF-8", ByteArrayInputStream(WebThemeState.current.css().toByteArray()))
            "m3e.css", "components.js" -> WebResourceResponse(if (path.endsWith("css")) "text/css" else "application/javascript", "UTF-8", open("kigtts/$path"))
            else -> WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }
    }
}
fun WebView.applyKigttsTheme() { evaluateJavascript(WebThemeState.current.script(), null) }
