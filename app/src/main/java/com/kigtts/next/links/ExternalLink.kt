package com.kigtts.next.links

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import java.net.URI
import java.util.Locale

enum class WebOpenMode(val wire: String) { QUICK_TEXT("quick-text"), CUSTOM_TAB("custom-tab") }
data class ExternalLink(val url: String? = null, val text: String = "", val title: String = "",
    val mode: WebOpenMode = WebOpenMode.QUICK_TEXT, val add: Boolean = false) {
    val trusted get() = url?.let(TrustedWebsite::contains) == true
}
object TrustedWebsite {
    fun contains(url: String): Boolean = runCatching {
        val uri = URI(url); val host = uri.host?.lowercase(Locale.ROOT)?.removeSuffix(".") ?: return false
        uri.scheme.equals("https", true) && uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
            (host == "lhtstudio.com" || host.endsWith(".lhtstudio.com"))
    }.getOrDefault(false)
    fun validate(url: String): String = url.also {
        val uri = URI(it)
        require(it.length <= 4096 && uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "请使用有效的 HTTPS 网址" }
    }
}
object ExternalLinkParser {
    fun parse(uri: Uri?): ExternalLink? {
        uri ?: return null
        val own = uri.scheme == "kigtts"
        val website = TrustedWebsite.contains(uri.toString()) && uri.path in setOf("/kigtts/open", "/kigtts/quick-text")
        if (!own && !website) return null
        val action = if (own) uri.host else uri.lastPathSegment
        if (action !in setOf("open", "quick-text")) return null
        require(uri.queryParameterNames.all { uri.getQueryParameters(it).size == 1 }) { "链接中有重复参数" }
        val add = when (uri.getQueryParameter("add")) { null, "false", "0" -> false; "true", "1" -> true; else -> error("添加选项无效") }
        val title = uri.getQueryParameter("title").orEmpty().take(120)
        val mode = when (uri.getQueryParameter("mode")) {
            null, "quick-text" -> WebOpenMode.QUICK_TEXT
            "custom-tab", "custom-tabs" -> WebOpenMode.CUSTOM_TAB
            else -> error("不支持这个打开方式")
        }
        val url = uri.getQueryParameter("url")?.let(TrustedWebsite::validate)
        val text = uri.getQueryParameter("text").orEmpty()
        require(text.length <= 20000 && (url != null || text.isNotBlank())) { "链接中没有可打开的内容" }
        require(url != null || mode == WebOpenMode.QUICK_TEXT) { "浏览器需要一个网址" }
        return ExternalLink(url, text, title, mode, add)
    }
    fun from(intent: Intent?): ExternalLink? {
        if (intent?.action !in setOf(Intent.ACTION_VIEW, NfcAdapter.ACTION_NDEF_DISCOVERED)) return null
        val direct = parse(intent?.data)
        if (direct != null) return direct
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        @Suppress("DEPRECATION")
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES).orEmpty()
        return messages.filterIsInstance<NdefMessage>().asSequence().flatMap { it.records.asSequence() }
            .mapNotNull { it.toUri() }.mapNotNull(::parse).firstOrNull()
    }
}
