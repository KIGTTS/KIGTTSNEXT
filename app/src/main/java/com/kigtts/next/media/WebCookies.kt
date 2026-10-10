package com.kigtts.next.media

import android.webkit.CookieManager

/** WebView JS and intercepted network requests share Android's persistent cookie store. */
interface WebCookieJar {
    fun header(url: String): String?
    fun receive(url: String, cookies: List<String>)
}
object WebCookies : WebCookieJar {
    fun enable() { CookieManager.getInstance().setAcceptCookie(true) }
    override fun header(url: String) = CookieManager.getInstance().getCookie(url)
    override fun receive(url: String, cookies: List<String>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(url, it) }
        if (cookies.isNotEmpty()) manager.flush()
    }
    fun clear(complete: () -> Unit) { CookieManager.getInstance().removeAllCookies { complete() } }
}
